package eventstore
package tcp

import akka.actor._
import pipeline._
import akka.io.{ Tcp, IO }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import util.{ CancellableAdapter, BidirectionalMap }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)

  case object Reconnected
  case object WaitReconnected

  private[eventstore] lazy val connectionLostFailure = Status.Failure(EsException(EsError.ConnectionLost))

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

  import ConnectionActor.{ Reconnected, WaitReconnected, connectionLostFailure }
  import context.dispatcher
  import context.system
  import settings._

  val init = EsPipelineInit(log, backpressure)

  var binding = new BidirectionalMap[Uuid, ActorRef]()
  var subscriptions = Set.empty[ActorRef] // TODO part of connected state

  var state: State = {
    connect("connecting", Duration.Zero)
    Connecting(Queue.empty)
  }

  val rcv: PartialFunction[Any, State] = {
    case Tcp.Connected(`address`, _)       => state rcvConnected sender
    case Tcp.CommandFailed(_: Tcp.Connect) => state.rcvNotConnected
    case x: Tcp.ConnectionClosed           => state rcvConnectionClosed x
    case x: OutLike                        => state rcvOutLike x
    case x: TcpPackageOut                  => state rcvPackageOut x
    case Terminated(x)                     => state rcvTerminated x
    case WaitReconnected                   => state rcvWaitReconnected sender
    case init.Event(x)                     => state rcvPackageIn x
    case Heartbeat                         => state.rcvHeartbeat
    case HeartbeatTimeout(x)               => state rcvHeartbeatTimeout x
  }

  def receive = new Receive {
    override def isDefinedAt(x: Any) = rcv isDefinedAt x
    override def apply(x: Any) = state = rcv apply x
  }

  def newPipeline(connection: ActorRef): ActorRef =
    context.actorOf(TcpPipelineHandler.props(init, connection, self))

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => defaultCredentials
  }

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender) getOrElse {
      val x = randomUuid
      log.debug("add sender {} for {}", sender, x)
      context watch sender
      binding = binding + (x, sender)
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

  sealed trait State {
    def rcvConnected(connection: ActorRef): State = this
    def rcvNotConnected: State = this
    def rcvConnectionClosed(x: Tcp.ConnectionClosed): State = this
    def rcvPackageOut(x: TcpPackageOut): State = this
    def rcvOutLike(x: OutLike): State = this
    def rcvHeartbeat: State = this
    def rcvHeartbeatTimeout(id: Long): State = this
    def rcvWaitReconnected(client: ActorRef): State = this
    def rcvPackageIn(x: TcpPackageIn): State = this
    def rcvTerminated(x: ActorRef): State = {
      binding.y(x).foreach {
        uuid =>
          binding = binding - x
          if (subscriptions contains x) {
            self ! TcpPackageOut(Unsubscribe, uuid, settings.defaultCredentials)
            subscriptions = subscriptions - x
          }
      }
      this
    }
  }

  sealed trait ConnectingOrReconnecting extends State {
    override def rcvConnected(connection: ActorRef): Connected = {
      log.info("connected to {}", address)
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)
      context watch connection
      context watch pipeline
      Connected(connection, pipeline, 0)
    }

    override def rcvNotConnected: State = {
      log.error("connection failed to {}", address)
      maybeReconnect getOrElse {
        context stop self
        this
      }
    }

    def maybeReconnect: Option[Reconnecting]
  }

  sealed trait ConnectedOrReconnecting extends State {
    def receiveIn(x: TcpPackageIn, f: TcpPackageOut => Unit) {
      log.debug(x.toString)
      x.message match {
        case Success(HeartbeatRequest) => f(TcpPackageOut(HeartbeatResponse, x.correlationId))
        case Success(Ping)             => f(TcpPackageOut(Pong, x.correlationId))
        case _                         => dispatch(x, f)
        // TODO reconnect on EsException(NotHandled(NotReady))
      }
    }

    def dispatch(pack: TcpPackageIn, f: TcpPackageOut => Unit) {
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
          case _: SubscribeCompleted =>
            log.warning("can not deliver {}, sender not found for correlationId: {}, unsubscribing", msg, correlationId)
            f(TcpPackageOut(Unsubscribe, correlationId, defaultCredentials))

          case _ => log.warning("can not deliver {}, sender not found for correlationId: {}", msg, correlationId)
        }
      }
    }

    def reconnect(reconnectionsLeft: Int, reconnectionDelay: FiniteDuration, clients: Set[ActorRef]): Reconnecting = {
      def reconnect(reconnectionDelay: FiniteDuration): Reconnecting = {
        connect("reconnecting", reconnectionDelay)
        val delay =
          if (reconnectionDelay == Duration.Zero) Settings.Default.reconnectionDelayMin
          else {
            val x = reconnectionDelay * 2
            if (x > reconnectionDelayMax) reconnectionDelayMax else x
          }
        Reconnecting(reconnectionsLeft - 1, delay, clients)
      }
      reconnect(Duration.fromNanos(reconnectionDelay.toNanos))
    }
  }

  case class Connecting(stash: Queue[TcpPackageOut]) extends ConnectingOrReconnecting {
    override def rcvConnected(connection: ActorRef) = {
      val connected = super.rcvConnected(connection)
      stash.foreach(connected.rcvPackageOut)
      connected
    }

    override def rcvOutLike(x: OutLike) = rcvPackageOut(tcpPack(x))
    override def rcvPackageOut(x: TcpPackageOut) = copy(stash enqueue x)
    override def maybeReconnect = None
  }

  case class Connected(connection: ActorRef, pipeline: ActorRef, heartbeatId: Long)
      extends ConnectedOrReconnecting {

    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def maybeReconnect(reason: String): State = {
      binding.yx.keySet.foreach(_ ! connectionLostFailure)
      if (!scheduled.isCancelled) scheduled.cancel()
      val template = "connection lost to {}: {}"
      if (maxReconnections == 0) {
        log.error(template, address, reason)
        context stop self
        this
      } else {
        log.warning(template, address, reason)
        reconnect(maxReconnections, reconnectionDelayMin, Set.empty)
      }
    }

    override def rcvConnectionClosed(x: Tcp.ConnectionClosed) = x match {
      case Tcp.PeerClosed         => maybeReconnect("peer closed")
      case Tcp.ErrorClosed(error) => maybeReconnect(error.toString)
      case _ =>
        log.info("closing connection to {}", address)
        this
    }

    override def rcvHeartbeatTimeout(id: Long) = {
      connection ! Tcp.Close
      maybeReconnect(s"no heartbeat within $heartbeatTimeout")
    }

    override def rcvHeartbeat = rcvPackageOut(TcpPackageOut(HeartbeatRequest))

    override def rcvTerminated(x: ActorRef) = {
      if (x == connection) maybeReconnect("connection actor died")
      else if (x == pipeline) {
        connection ! Tcp.Abort
        maybeReconnect("pipeline actor died")
      } else super.rcvTerminated(x)
    }

    override def rcvPackageOut(pack: TcpPackageOut) = {
      log.debug(pack.toString)
      pipeline ! init.command(pack)
      this
    }

    override def rcvPackageIn(x: TcpPackageIn) = {
      scheduled.cancel()
      receiveIn(x, f => rcvPackageOut(f))
      this.copy(heartbeatId = heartbeatId + 1)
    }

    override def rcvOutLike(x: OutLike) = rcvPackageOut(tcpPack(x))

    override def rcvWaitReconnected(client: ActorRef) = {
      client ! Reconnected
      this
    }
  }

  case class Reconnecting(reconnectionsLeft: Int, reconnectionDelay: FiniteDuration, clients: Set[ActorRef])
      extends ConnectingOrReconnecting with ConnectedOrReconnecting {

    override def rcvPackageIn(x: TcpPackageIn) = {
      receiveIn(x, _ => Unit)
      this
    }

    override def rcvConnected(connection: ActorRef) = {
      val connected = super.rcvConnected(connection)
      clients.foreach(_ ! Reconnected)
      connected
    }

    override def rcvWaitReconnected(client: ActorRef): Reconnecting = copy(clients = clients + client)

    override def maybeReconnect =
      if (reconnectionsLeft == 0) None
      else Some(reconnect(reconnectionsLeft, reconnectionDelay, clients))

    override def rcvOutLike(x: OutLike) = {
      sender ! connectionLostFailure
      this
    }

    override def rcvPackageOut(x: TcpPackageOut) = {
      sender ! connectionLostFailure
      this
    }
  }
}