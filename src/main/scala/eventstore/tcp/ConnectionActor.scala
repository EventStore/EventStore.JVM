package eventstore
package tcp

import akka.actor.{ IO => _, _ }
import akka.io.{ TcpPipelineHandler, Tcp, IO }
import util.{ CancellableAdapter, BidirectionalMap }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)
}

class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import context.system
  import context.dispatcher
  import settings._

  val init = EsPipelineInit(log, backpressureSettings)

  override def preStart() {
    log.debug(s"connecting to $address")
    tcp ! connect
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]()

  def receiveTerminated: Receive = {
    case Terminated(actor) => binding.y(actor).foreach {
      uuid =>
        binding = binding - actor
        if (subscriptions contains actor) {
          self ! TcpPackageOut(uuid, UnsubscribeFromStream, settings.defaultCredentials)
          subscriptions = subscriptions - actor
        }
    }
  }

  var subscriptions = Set[ActorRef]() // TODO part of connected state

  def receive = connecting(Queue(), 0)

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

  def connecting(stash: Queue[TcpPackageOut], reconnectionsLeft: Int): Receive = {
    subscriptions = Set()
    val receive: Receive = {
      case init.Event(in) => receiveIn(in, _ => Unit)

      case Tcp.Connected(remote, _) =>
        log.info(s"connected to $remote")

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

        context become connected(connection, pipeline, send, 0)

      case Tcp.CommandFailed(_: Tcp.Connect) =>
        log.error(s"connection failed to $address")
        if (reconnectionsLeft == 0) {
          // TODO notify senders about this
          context stop self
        } else {
          reconnect()
          context become connecting(stash, reconnectionsLeft - 1)
        }

      case x: OutLike =>
        log.debug(s"received $x while not connected, adding to stash")
        context become connecting(stash enqueue tcpPack(x), reconnectionsLeft)

      case pack: TcpPackageOut => context become connecting(stash enqueue pack, reconnectionsLeft)
    }

    receive orElse receiveTerminated
  }

  def connected(connection: ActorRef, pipeline: ActorRef, send: TcpPackageOut => Unit, heartbeatId: Long): Receive = {

    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)),
      system.scheduler.scheduleOnce(heartbeatInterval, self, HeartbeatInterval))

    def maybeReconnect(reason: String) {
      if (!scheduled.isCancelled) scheduled.cancel()
      val msg = s"connection lost to $address: $reason"
      if (settings.maxReconnections == 0) {
        log.error(msg)
        context stop self
      } else {
        log.warning(msg)
        reconnect()
        context become connecting(Queue(), maxReconnections)
      }
    }

    val receive: Receive = {
      case init.Event(in) =>
        scheduled.cancel()
        receiveIn(in, send)
        context become connected(connection, pipeline, send, heartbeatId + 1)

      case x: OutLike               => send(tcpPack(x))
      case x: TcpPackageOut         => send(x)
      case HeartbeatInterval        => send(TcpPackageOut(HeartbeatRequest))
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
        case _                      => log.info(s"closing connection to $address")
      }
    }

    receive orElse receiveTerminated
  }

  def reconnect() {
    if (reconnectionDelay == Duration.Zero) {
      log.info(s"reconnecting to $address")
      tcp ! connect
    } else {
      log.info(s"reconnecting to $address in $reconnectionDelay")
      system.scheduler.scheduleOnce(reconnectionDelay, tcp, connect)
    }
  }

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => settings.defaultCredentials
  }

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender) getOrElse {
      val x = newUuid
      log.debug(s"add sender $sender for $x")
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
          log.warning(s"can not deliver $msg, sender not found for correlationId: $correlationId")
          system.deadLetters ! msg
      }
    }
  }

  def connect = Tcp.Connect(address, timeout = Some(connectionTimeout))

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(id: Long)
  case object HeartbeatInterval
}