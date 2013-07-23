package eventstore
package tcp

import akka.actor.{Actor, ActorRef, ActorLogging, Deploy}
import akka.io._
import akka.io.TcpPipelineHandler.{WithinActorContext, Init}
import java.nio.ByteOrder
import java.net.InetSocketAddress
import scala.collection.immutable.Queue
import util.BidirectionalMap

/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(settings: Settings = Settings.default) extends Actor with ActorLogging {

  import Tcp._
  import context.system

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

  def receiveWhenConnecting(stash: Queue[TcpPackage[Out]] = Queue(), reconnectionsLeft: Int = settings.maxReconnections): Receive = {
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


    case CommandFailed(msg: Connect) =>

      if (reconnectionsLeft == 0) {
        log.error(s"connection failed to ${msg.remoteAddress}")
        context stop self
      } else {
        log.warning(s"connection failed to ${msg.remoteAddress}, retrying...")
        connect()
        context become receiveWhenConnecting(stash, reconnectionsLeft - 1)
      }

    case message: Out =>
      val segment = tcpPackage(message)
      log.debug(s"received $message in disconnected state, adding to stash")
      context become receiveWhenConnecting(stash.enqueue(segment))
  }

  def receiveWhenConnected(pipeline: ActorRef,
                           init: Init[WithinActorContext, TcpPackage[Out], TcpPackage[In]]): Receive = {

    {
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

      case message: Out =>
        val segment = tcpPackage(message)
        pipeline ! init.Command(segment)


      case msg: ConnectionClosed =>

        def maybeReconnect() {
          if (settings.maxReconnections == 0) {
            context stop self
          } else {
            log.info(s"reconnecting to $address")
            connect()
            context become receiveWhenConnecting()
          }
        }

        msg match {
          case PeerClosed =>
            log.error(s"connection lost to $address")
            maybeReconnect()

          case ErrorClosed(error) =>
            log.error(s"connection lost to $address due to error: $error")
            maybeReconnect()

          case _ =>
        }
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