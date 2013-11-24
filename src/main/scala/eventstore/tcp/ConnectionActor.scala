package eventstore
package tcp

import akka.actor.{ Terminated, Actor, ActorRef, ActorLogging, Status }
import akka.io._
import akka.io.TcpPipelineHandler.{ WithinActorContext, Init }
import java.nio.ByteOrder
import util.{ CancellableAdapter, BidirectionalMap }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(settings: Settings = Settings.Default) extends Actor with ActorLogging {

  def this() = this(Settings.Default)

  import Tcp._
  import context.system
  import context.dispatcher
  import settings._

  val init = TcpPipelineHandler.withLogger(log,
    new MessageByteStringAdapter >>
      new LengthFieldFrame(
        maxSize = 64 * 1024 * 1024,
        byteOrder = ByteOrder.LITTLE_ENDIAN,
        lengthIncludesHeader = false) >>
      new TcpReadWriteAdapter >>
      new BackpressureBuffer(
        lowBytes = backpressureSettings.lowWatermark,
        highBytes = backpressureSettings.highWatermark,
        maxBytes = backpressureSettings.maxCapacity))

  override def preStart() {
    log.debug(s"connecting to $address")
    tcp ! connect
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]()

  def terminated: Receive = {
    case Terminated(actor) => binding = binding - actor
  }

  def receive = connecting()

  def connecting(
    stash: Queue[TcpPackageOut] = Queue(),
    reconnectionsLeft: Int = maxReconnections): Receive = terminated orElse {
    case Connected(remote, _) =>
      log.info(s"connected to $remote")

      val connection = sender

      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, self))

      connection ! Register(pipeline)

      def send(pack: TcpPackageOut) {
        log.debug(pack.toString)
        pipeline ! init.command(pack)
      }

      stash.foreach(send)

      context become connected(connection, send, init)

    case CommandFailed(_: Connect) =>
      log.error(s"connection failed to $address")
      if (reconnectionsLeft == 0) context stop self
      else {
        reconnect()
        context become connecting(stash, reconnectionsLeft - 1)
      }

    case x: OutLike =>
      log.debug(s"received $x while not connected, adding to stash")
      val pack = tcpPack(x)
      context become connecting(stash enqueue pack)
  }

  def connected(
    connection: ActorRef,
    send: TcpPackageOut => Unit,
    init: Init[WithinActorContext, TcpPackageOut, TcpPackageIn],
    packNumber: Int = -1): Receive = terminated orElse {

    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatTimeout, self, HeartbeatTimeout(packNumber)),
      system.scheduler.scheduleOnce(heartbeatInterval, self, HeartbeatInterval))

    def maybeReconnect() {
      if (settings.maxReconnections == 0) context stop self
      else {
        reconnect()
        context become connecting()
      }
    }

    {
      case init.Event(pack @ TcpPackageIn(correlationId, msg)) =>
        log.debug(pack.toString)
        scheduled.cancel()
        msg match {
          case Success(HeartbeatRequest) => send(TcpPackageOut(correlationId, HeartbeatResponse))
          case Success(Ping)             => send(TcpPackageOut(correlationId, Pong))
          case _                         => dispatch(pack)
        }
        context become connected(connection, send, init, packNumber + 1)

      case x: OutLike        => send(tcpPack(x))

      case HeartbeatInterval => send(TcpPackageOut(HeartbeatRequest))

      case HeartbeatTimeout(`packNumber`) =>
        log.error(s"no heartbeat within $heartbeatTimeout")
        connection ! Close
        maybeReconnect()

      case closed: ConnectionClosed =>
        scheduled.cancel()
        closed match {
          case PeerClosed =>
            log.error(s"connection lost to $address")
            maybeReconnect()

          case ErrorClosed(error) =>
            log.error(s"connection lost to $address due to error: $error")
            maybeReconnect()

          case _ => log.info(s"closing connection to $address")
        }

      case Terminated(`connection`) =>
        scheduled.cancel()
        log.error(s"connection lost to $address")
        maybeReconnect()
    }
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

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender).getOrElse {
      val x = newUuid
      log.debug(s"add sender $sender for $x")
      context watch sender
      binding = binding.+(x, sender)
      x
    }

    val (out, credentials) = message match {
      case WithCredentials(x, c) => x -> Some(c)
      case x: Out                => x -> settings.defaultCredentials
    }

    TcpPackageOut(correlationId, out, credentials)
  }

  def dispatch(pack: TcpPackageIn) {
    val msg = pack.message match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    val correlationId = pack.correlationId
    binding.x(correlationId) match {
      case Some(channel) => channel ! msg
      case None => msg match {
        case Pong | HeartbeatResponse =>
        case _ =>
          log.warning(s"sender not found by correlationId: $correlationId for $msg")
          system.deadLetters ! msg
      }
    }
  }

  def connect = Tcp.Connect(address, timeout = Some(connectionTimeout))

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(packNumber: Int)
  case object HeartbeatInterval
}