package eventstore
package tcp

import akka.actor.{Actor, ActorRef, ActorLogging, Deploy}
import akka.io._
import akka.io.TcpPipelineHandler.{WithinActorContext, Init}
import java.nio.ByteOrder
import scala.collection.immutable.Queue
import util.BidirectionalMap

/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import Tcp._
  import context.system
  import context.dispatcher

  val address = settings.address
  val tcp = IO(Tcp)


  override def preStart() {
    log.debug(s"connecting to $address")
    connect()
  }

  def connect() {
    tcp ! Connect(address)
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]() // todo clean on expiry or somehow  

  def receive = receiveWhenConnecting()

  def receiveWhenConnecting(stash: Queue[TcpPackage[Out]] = Queue(),
                            reconnectionsLeft: Int = settings.maxReconnections): Receive = {
    case Connected(remote, _) =>
      log.info(s"connected to $remote")

      val connection = sender

      val maxSize = 64 * 1024 * 1024

      val init = TcpPipelineHandler.withLogger(log,
        new MessageByteStringAdapter >>
          new FixedLengthFieldFrame(
            maxSize = maxSize,
            byteOrder = ByteOrder.LITTLE_ENDIAN,
            lengthIncludesHeader = false) >>
          new TcpReadWriteAdapter >>
          new BackpressureBuffer(lowBytes = maxSize / 1024 / 1024, highBytes = maxSize / 1024, maxBytes = 4 * maxSize))

      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, self).withDeploy(Deploy.local))

      connection ! Register(pipeline)
      stash.foreach(segment => pipeline ! init.Command(segment))
      context.become(receiveWhenConnected(pipeline, init))

    case CommandFailed(_: Connect) =>
      log.error(s"connection failed to $address")
      if (reconnectionsLeft == 0) context stop self
      else {
        reconnect()
        context become receiveWhenConnecting(stash, reconnectionsLeft - 1)
      }

    case message: Out =>
      val segment = tcpPackage(message)
      log.debug(s"received $message while not connected, adding to stash")
      context become receiveWhenConnecting(stash.enqueue(segment))
  }

  def receiveWhenConnected(pipeline: ActorRef,
                           init: Init[WithinActorContext, TcpPackage[Out], TcpPackage[In]]): Receive = {

    case init.Event(TcpPackage(correlationId, message: In, _)) => message match {
      case HeartbeatRequestCommand => pipeline ! init.Command(TcpPackage(correlationId, HeartbeatResponseCommand, Some(AuthData("admin", "changeit"))))
      case _ =>
        val actor = binding.x(correlationId) match {
          case Some(channel) => channel
          case None =>
            log.warning(s"sender not found for $message")
            Actor.noSender
        }
        actor ! message
    }

    case out: Out => pipeline ! init.Command(tcpPackage(out))

    case closed: ConnectionClosed =>
      def maybeReconnect() {
        if (settings.maxReconnections == 0) context stop self
        else {
          reconnect()
          context become receiveWhenConnecting()
        }
      }

      closed match {
        case PeerClosed =>
          log.error(s"connection lost to $address")
          maybeReconnect()

        case ErrorClosed(error) =>
          log.error(s"connection lost to $address due to error: $error")
          maybeReconnect()

        case _ =>
      }
  }

  def reconnect() {
    val reconnectionDelay = settings.reconnectionDelay
    if (reconnectionDelay.length == 0) {
      log.info(s"reconnecting to $address")
      connect()
    } else {
      log.info(s"reconnecting to $address in $reconnectionDelay")
      system.scheduler.scheduleOnce(reconnectionDelay, tcp, Connect(address))
    }
  }

  def tcpPackage(message: Out) = {
    val correlationId = binding.y(sender).getOrElse {
      val x = newUuid
      log.info(s"add sender $sender for $x")
      binding = binding.+(x, sender)
      x
    }

    TcpPackage(correlationId, message, Some(AuthData("admin", "changeit")))
  }
}