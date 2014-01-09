package eventstore
package tcp

import akka.actor.{ IO => _, _ }
import akka.io.{ TcpPipelineHandler, Tcp, IO }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import util.{ CancellableAdapter, BidirectionalMap }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)

  case object Reconnected
  case object WaitReconnected
}

class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import ConnectionActor.{ Reconnected, WaitReconnected }
  import context.dispatcher
  import context.system
  import settings._

  val init = EsPipelineInit(log, backpressureSettings)

  var binding = new BidirectionalMap[Uuid, ActorRef]()
  var subscriptions = Set.empty[ActorRef] // TODO part of connected state

  val rcvTerminated: Receive = {
    case Terminated(actor) => binding.y(actor).foreach {
      uuid =>
        binding = binding - actor
        if (subscriptions contains actor) {
          self ! TcpPackageOut(uuid, UnsubscribeFromStream, settings.defaultCredentials)
          subscriptions = subscriptions - actor
        }
    }
  }

  lazy val noConnectionFailure = Status.Failure(EsException(EsError.ConnectionLost))

  def receive = connecting(Queue.empty)

  def connecting(stash: Queue[TcpPackageOut]): Receive = {
    connect("connecting", Duration.Zero)
    rcvConnected(stash, Set.empty, 0, reconnectionDelayMin) orElse rcvTerminated orElse {
      case x: OutLike       => context become connecting(stash enqueue tcpPack(x))
      case x: TcpPackageOut => context become connecting(stash enqueue x)
    }
  }

  def reconnecting(clients: Set[ActorRef], reconLeft: Int, reconDelay: FiniteDuration): Receive =
    rcvConnected(Queue.empty, clients, reconLeft, reconDelay) orElse rcvTerminated orElse {
      case x: OutLike       => sender ! noConnectionFailure
      case x: TcpPackageOut => sender ! noConnectionFailure
      case init.Event(in)   => receiveIn(in, _ => Unit)
      case WaitReconnected  => context become reconnecting(clients + sender, reconLeft, reconDelay)
    }

  def connected(connection: ActorRef, pipeline: ActorRef, send: TcpPackageOut => Unit, heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)),
      system.scheduler.scheduleOnce(heartbeatInterval, self, HeartbeatInterval))

    def maybeReconnect(reason: String) {
      if (!scheduled.isCancelled) scheduled.cancel()
      val template = "connection lost to {}: {}"
      if (maxReconnections == 0) {
        log.error(template, address, reason)
        context stop self
      } else {
        log.warning(template, address, reason)
        reconnect(maxReconnections, reconnectionDelayMin)
      }
    }

    val receive: Receive = {
      case x: OutLike               => send(tcpPack(x))
      case x: TcpPackageOut         => send(x)
      case HeartbeatInterval        => send(TcpPackageOut(HeartbeatRequest))
      case WaitReconnected          => sender ! Reconnected
      case Terminated(`connection`) => maybeReconnect("connection actor died")

      case Terminated(`pipeline`) =>
        connection ! Tcp.Abort
        maybeReconnect("pipeline actor died")

      case HeartbeatTimeout(`heartbeatId`) =>
        connection ! Tcp.Close
        maybeReconnect(s"no heartbeat within $heartbeatTimeout")

      case closed: Tcp.ConnectionClosed => closed match {
        case Tcp.PeerClosed         => maybeReconnect("peer closed")
        case Tcp.ErrorClosed(error) => maybeReconnect(error.toString)
        case _                      => log.info("closing connection to {}", address)
      }

      case init.Event(in) =>
        scheduled.cancel()
        receiveIn(in, send)
        context become connected(connection, pipeline, send, heartbeatId + 1)
    }

    receive orElse rcvTerminated
  }

  def rcvConnected(
    stash: Queue[TcpPackageOut],
    clients: Set[ActorRef],
    reconnectionsLeft: Int,
    reconnectionDelay: FiniteDuration): Receive = {
    case Tcp.Connected(remote, _) =>
      log.info("connected to {}", remote)

      val connection = sender
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)
      context watch connection
      context watch pipeline

      def send(pack: TcpPackageOut) {
        log.debug(pack.toString)
        pipeline ! init.command(pack)
      }

      stash.foreach(send)
      clients.foreach(_ ! Reconnected)
      context become connected(connection, pipeline, send, 0)

    case Tcp.CommandFailed(_: Tcp.Connect) =>
      log.error("connection failed to {}", address)
      binding.yx.keySet.foreach(_ ! noConnectionFailure)
      if (reconnectionsLeft == 0) context stop self
      else reconnect(reconnectionsLeft, reconnectionDelay)
  }

  def newPipeline(connection: ActorRef): ActorRef =
    context.actorOf(TcpPipelineHandler.props(init, connection, self))

  def receiveIn(x: TcpPackageIn, f: TcpPackageOut => Unit) {
    log.debug(x.toString)
    x.message match {
      case Success(HeartbeatRequest) => f(TcpPackageOut(x.correlationId, HeartbeatResponse))
      case Success(Ping)             => f(TcpPackageOut(x.correlationId, Pong))
      case _                         => dispatch(x)
    }
  }

  def reconnect(reconnectionsLeft: Int, reconnectionDelay: FiniteDuration) {
    connect("reconnecting", reconnectionDelay)

    val delay =
      if (reconnectionDelay == Duration.Zero) Settings.Default.reconnectionDelayMin
      else {
        val x = reconnectionDelay * 2
        if (x > reconnectionDelayMax) reconnectionDelayMax else Duration.fromNanos(x.toNanos)
      }
    context become reconnecting(Set.empty, reconnectionsLeft - 1, delay)
  }

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => settings.defaultCredentials
  }

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender) getOrElse {
      val x = newUuid
      log.debug("add sender {} for {}", sender, x)
      context watch sender
      binding = binding + (x, sender)
      x
    }
    TcpPackageOut(correlationId, message.out, credentials(message))
  }

  def dispatch(pack: TcpPackageIn) {
    val msg = pack.message match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    val correlationId = pack.correlationId
    binding.x(correlationId) match {
      case Some(channel) =>
        PartialFunction.condOpt(msg) {
          case x: SubscribeCompleted => subscriptions = subscriptions + channel
        }
        channel ! msg

      case None => msg match {
        case Pong | HeartbeatResponse | UnsubscribeCompleted =>
        case _ =>
          log.warning("can not deliver {}, sender not found for correlationId: {}", msg, correlationId)
          system.deadLetters ! msg
      }
    }
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

  private case class HeartbeatTimeout(id: Long)
  private case object HeartbeatInterval
}