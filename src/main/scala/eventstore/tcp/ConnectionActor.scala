package eventstore
package tcp

import akka.actor.{Cancellable, Actor, ActorRef, ActorLogging}
import akka.io._
import akka.io.TcpPipelineHandler.{WithinActorContext, Init}
import java.nio.ByteOrder
import scala.collection.immutable.Queue
import util.{CancellableAdapter, BidirectionalMap}

/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import Tcp._
  import context.system
  import context.dispatcher
  import settings._

  case class HeartbeatTimeout(packNumber: Long)
  case object HeartbeatInterval

  val tcp = IO(Tcp)


  override def preStart() {
    log.debug(s"connecting to $address")
    tcp ! connect
  }

  def connect = Tcp.Connect(address, timeout = Some(connectionTimeout))

  var binding = new BidirectionalMap[Uuid, ActorRef]() // todo clean on expiry or somehow  

  def receive = connecting()

  def connecting(stash: Queue[TcpPackage[Out]] = Queue(),
                            reconnectionsLeft: Int = maxReconnections): Receive = {
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
      stash.foreach(segment => pipeline ! init.Command(segment))
      context become connected(connection, pipeline, init)

    case CommandFailed(_: Connect) =>
      log.error(s"connection failed to $address")
      if (reconnectionsLeft == 0) context stop self
      else {
        reconnect()
        context become connecting(stash, reconnectionsLeft - 1)
      }

    case message: Out =>
      val segment = tcpPackage(message)
      log.debug(s"received $message while not connected, adding to stash")
      context become connecting(stash.enqueue(segment))
  }

  def connected(connection: ActorRef,
                pipeline: ActorRef,
                init: Init[WithinActorContext, TcpPackage[Out], TcpPackage[In]]): Receive = {

    var packNumber = 0L

    def schedule(): Cancellable = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatTimeout, self, HeartbeatTimeout),
      system.scheduler.scheduleOnce(heartbeatInterval, self, HeartbeatInterval))

    var scheduled = schedule() // TODO

    def send(pack: TcpPackage[Out]) {
      log.debug(s"sending $pack")
      pipeline ! init.command(pack)
    }

    def reschedule() {
      scheduled = schedule()
    }

    def maybeReconnect() {
      if (settings.maxReconnections == 0) context stop self
      else {
        reconnect()
        context become connecting()
      }
    }

    {
      case init.Event(pack@TcpPackage(correlationId, msg, _)) =>
        log.debug(s"received $pack")
        reschedule()
        packNumber = packNumber + 1

        msg match {
          case HeartbeatResponseCommand =>
          case HeartbeatRequestCommand => send(TcpPackage(correlationId, HeartbeatResponseCommand, Some(AuthData.defaultAdmin)))
          case _ => dispatch(pack)
        }

      case HeartbeatInterval => send(TcpPackage(HeartbeatRequestCommand))

      case HeartbeatTimeout(n) if n == packNumber =>
        log.error(s"no heartbeat within $heartbeatTimeout")
        connection ! Close
        maybeReconnect()

      case out: Out => send(tcpPackage(out))

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
    if (reconnectionDelay.length == 0) {
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
      log.info(s"add sender $sender for $x")
      binding = binding.+(x, sender)
      x
    }

    TcpPackage(correlationId, message, Some(AuthData.defaultAdmin))
  }

  def dispatch(pack: TcpPackage[In]) {
    val msg = pack.message
    val actor = binding.x(pack.correlationId) match {
      case Some(channel) => channel
      case None =>
        log.warning(s"sender not found for $msg")
        Actor.noSender
    }
    actor ! msg
  }
}