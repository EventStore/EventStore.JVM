package eventstore
package tcp

import akka.actor._
import pipeline._
import akka.io.{ Tcp, IO }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import util.{ CancellableAdapter, BidirectionalMap, DelayedRetry }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)

  case object Reconnected
  case object WaitReconnected

  /**
   * Java API
   */
  def getProps(): Props = props()

  /**
   * Java API
   */
  def getProps(settings: Settings): Props = props(settings)

  /**
   * Java API
   */
  def reconnected: Reconnected.type = Reconnected

  /**
   * Java API
   */
  def waitReconnected: WaitReconnected.type = WaitReconnected
}

private[eventstore] class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import ConnectionActor.{ Reconnected, WaitReconnected }
  import context.dispatcher
  import context.system
  import settings._

  val init = EsPipelineInit(log, backpressure)

  var binding = new BidirectionalMap[Uuid, ActorRef]()
  var subscriptions = Set.empty[ActorRef] // TODO part of connected state

  val rcvTerminated: Receive = {
    // TODO `binding.contains`
    case Terminated(x) =>
      binding.y(x).foreach {
        uuid =>
          binding = binding - x
          if (subscriptions contains x) {
            self ! TcpPackageOut(Unsubscribe, uuid, settings.defaultCredentials)
            subscriptions = subscriptions - x
          }
      }
  }

  def receive = {
    connect("connecting", Duration.Zero)
    connecting(Queue.empty)
  }

  def connecting(stash: Queue[TcpPackageOut]): Receive = {
    def stashPack(out: TcpPackageOut) = context become connecting(stash enqueue out)

    def onPipeline(pipeline: ActorRef) = stash.foreach(sendPack(pipeline, _))

    rcvOut(stashPack) orElse rcvConnected(onPipeline) orElse rcvConnectFailed(None) orElse rcvTerminated
  }

  def connected(connection: ActorRef, pipeline: ActorRef, heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def maybeReconnect(reason: String) = {
      val failure = connectionLostFailure
      binding.yx.keySet.foreach(_ ! failure)
      if (!scheduled.isCancelled) scheduled.cancel()
      val template = "connection lost to {}: {}"

      DelayedRetry.opt(maxReconnections, reconnectionDelayMin, reconnectionDelayMax) match {
        case None =>
          log.error(template, address, reason)
          context stop self

        case Some(retry) =>
          log.warning(template, address, reason)
          connect("reconnecting", retry.delay)
          context become reconnecting(retry, Set.empty)
      }
    }

    val receive: Receive = {
      case x: Tcp.ConnectionClosed => x match {
        case Tcp.PeerClosed         => maybeReconnect("peer closed")
        case Tcp.ErrorClosed(error) => maybeReconnect(error.toString)
        case _                      => log.info("closing connection to {}", address)
      }

      case Terminated(`connection`) => maybeReconnect("connection actor died")

      case Terminated(`pipeline`) =>
        connection ! Tcp.Abort
        maybeReconnect("pipeline actor died")

      case WaitReconnected => sender() ! Reconnected

      case init.Event(x) =>
        scheduled.cancel()
        dispatch(x, sendPack(pipeline, _))
        context become connected(connection, pipeline, heartbeatId + 1)

      case Heartbeat => sendPack(pipeline, TcpPackageOut(HeartbeatRequest))

      case HeartbeatTimeout(id) => if (id == heartbeatId) {
        connection ! Tcp.Close
        maybeReconnect(s"no heartbeat within $heartbeatTimeout")
      }
    }

    receive orElse rcvOut(sendPack(pipeline, _)) orElse rcvTerminated
  }

  def reconnecting(retry: DelayedRetry, clients: Set[ActorRef]): Receive = {
    def reconnect = retry.next.map { retry =>
      connect("reconnecting", retry.delay)
      reconnecting(retry, clients)
    }

    def onPipeline(pipeline: ActorRef) = clients.foreach(_ ! Reconnected)

    val receive: Receive = {
      case WaitReconnected               => context become reconnecting(retry, clients + sender())
      case init.Event(x)                 => dispatch(x, _ => Unit)
      case _: OutLike | _: TcpPackageOut => sender() ! connectionLostFailure
    }

    receive orElse rcvConnected(onPipeline) orElse rcvConnectFailed(reconnect) orElse rcvTerminated
  }

  def rcvConnectFailed(recover: => Option[Receive]): Receive = {
    case Tcp.CommandFailed(_: Tcp.Connect) =>
      val template = "connection failed to {}"
      recover match {
        case Some(x) =>
          log.warning(template, address)
          context become x
        case None =>
          log.error(template, address)
          context stop self
      }
  }

  def rcvConnected(onPipeline: ActorRef => Any): Receive = {
    case Tcp.Connected(`address`, _) =>
      log.info("connected to {}", address)
      val connection = sender()
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)
      onPipeline(pipeline)
      context watch connection
      context watch pipeline
      context become connected(connection, pipeline, 0)
  }

  def rcvOut(sendPack: TcpPackageOut => Any): Receive = {
    case x: OutLike       => sendPack(tcpPack(x))
    case x: TcpPackageOut => sendPack(x)
  }

  def dispatch(in: TcpPackageIn, reply: TcpPackageOut => Unit): Unit = {
    log.debug(in.toString)

    def forward(msg: Any) = {
      val correlationId = in.correlationId
      binding.x(correlationId) match {
        case Some(client) =>
          msg match {
            case _: SubscribeCompleted => subscriptions = subscriptions + client
            case _                     =>
          }
          client ! msg

        case None => msg match {
          case Pong | HeartbeatResponse | UnsubscribeCompleted =>
          case _: SubscribeCompleted =>
            log.warning("can not deliver {}, sender not found for correlationId: {}, unsubscribing", msg, correlationId)
            reply(TcpPackageOut(Unsubscribe, correlationId, defaultCredentials))

          case _ => log.warning("can not deliver {}, sender not found for correlationId: {}", msg, correlationId)
        }
      }
    }

    in.message match {
      // TODO reconnect on EsException(NotHandled(NotReady))
      case Success(HeartbeatRequest) => reply(TcpPackageOut(HeartbeatResponse, in.correlationId))
      case Success(Ping)             => reply(TcpPackageOut(Pong, in.correlationId))
      case Success(x)                => forward(x)
      case Failure(x)                => forward(Status.Failure(x))
    }
  }

  def sendPack(pipeline: ActorRef, pack: TcpPackageOut): Unit = {
    log.debug(pack.toString)
    pipeline ! init.command(pack)
  }

  def newPipeline(connection: ActorRef): ActorRef =
    context.actorOf(TcpPipelineHandler.props(init, connection, self))

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => defaultCredentials
  }

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender()) getOrElse {
      val x = randomUuid
      log.debug("add sender {} for {}", sender(), x)
      context watch sender()
      binding = binding + (x, sender())
      x
    }
    TcpPackageOut(message.out, correlationId, credentials(message))
  }

  def connect(label: String, in: FiniteDuration) {
    val connect = Tcp.Connect(address, timeout = Some(connectionTimeout))
    if (in == Duration.Zero) {
      log.info("{} to {}", label, address)
      tcp ! connect
    } else {
      log.info("{} to {} in {}", label, address, in)
      system.scheduler.scheduleOnce(in, tcp, connect)
    }
  }

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(id: Long)
  case object Heartbeat

  private def connectionLostFailure = Status.Failure(EsException(EsError.ConnectionLost))
}