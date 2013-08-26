package eventstore
package tcp

import akka.actor.{ Actor, ActorRef, ActorLogging }
import akka.io._
import akka.io.TcpPipelineHandler.{ WithinActorContext, Init }
import java.nio.ByteOrder
import util.{ CancellableAdapter, BidirectionalMap }
import scala.collection.immutable.Queue
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  def this() = this(Settings())

  import Tcp._
  import context.system
  import context.dispatcher
  import settings._

  override def preStart() {
    log.debug(s"connecting to $address")
    tcp ! connect
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]() // todo clean on expiry or somehow

  def receive = connecting()

  def connecting(stash: Queue[TcpPackageOut] = Queue(), reconnectionsLeft: Int = maxReconnections): Receive = {
    case Connected(remote, _) =>
      log.info(s"connected to $remote")

      val connection = sender

      val init = TcpPipelineHandler.withLogger(log,
        new MessageByteStringAdapter >>
          new FixedLengthFieldFrame(
            maxSize = 64 * 1024 * 1024,
            byteOrder = ByteOrder.LITTLE_ENDIAN,
            lengthIncludesHeader = false) >>
          new TcpReadWriteAdapter >>
          new BackpressureBuffer(
            lowBytes = backpressureLowWatermark,
            highBytes = backpressureHighWatermark,
            maxBytes = backpressureMaxCapacity))

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

    case message: Out =>
      log.debug(s"received $message while not connected, adding to stash")
      val pack = tcpPackage(message)
      context become connecting(stash enqueue pack)
  }

  def connected(
    connection: ActorRef,
    send: TcpPackageOut => Unit,
    init: Init[WithinActorContext, TcpPackageOut, TcpPackageIn],
    packNumber: Int = -1): Receive = {

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
          case HeartbeatResponseCommand =>
          case HeartbeatRequestCommand => send(TcpPackageOut(correlationId, HeartbeatResponseCommand))
          case Pong =>
          case Ping => send(TcpPackageOut(correlationId, Pong))
          case _ => dispatch(pack)
        }
        context become connected(connection, send, init, packNumber + 1)

      case out: Out => send(tcpPackage(out))

      case HeartbeatInterval => send(TcpPackageOut(HeartbeatRequestCommand))

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

  def tcpPackage(message: Out) = {
    val correlationId = binding.y(sender).getOrElse {
      val x = newUuid
      log.debug(s"add sender $sender for $x")
      binding = binding.+(x, sender)
      x
    }

    TcpPackageOut(correlationId, message, Some(UserCredentials.defaultAdmin))
  }

  def dispatch(pack: TcpPackageIn) {
    val msg = pack.message
    val actor = binding.x(pack.correlationId) match {
      case Some(channel) => channel
      case None =>
        log.warning(s"sender not found for $msg")
        Actor.noSender
    }
    actor ! msg
  }

  def connect = Tcp.Connect(address, timeout = Some(connectionTimeout))

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(packNumber: Int)
  case object HeartbeatInterval
}