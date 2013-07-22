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
class ConnectionActor(address: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  connect()

  def connect() {
    IO(Tcp) ! Connect(address)
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]() // todo clean on expiry or somehow  

  def receive = receiveWhenConnecting()

  def receiveWhenConnecting(stash: Queue[TcpPackage[Out]] = Queue()): Receive = {
    case connected@Connected(remote, local) =>
      log.info(s"Connected to $remote from $local")

      val connection = sender

      val init = TcpPipelineHandler.withLogger(log,
        new MessageByteStringAdapter >>
          new FixedLengthFieldFrame(
            maxSize = 64 * 1024 * 1024,
            byteOrder = ByteOrder.LITTLE_ENDIAN,
            lengthIncludesHeader = false) >>
          new TcpReadWriteAdapter >>
          new BackpressureBuffer(lowBytes = 100, highBytes = 1000, maxBytes = 1000000))

      val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, self).withDeploy(Deploy.local))

      connection ! Register(pipeline)
      stash.foreach(segment => pipeline ! init.Command(segment))

      context.become(receiveWhenConnected(pipeline, init))

    case command@CommandFailed(_: Connect) =>
      log.warning(command.toString)
      context stop self

    case message: Out =>
      val segment = tcpPackage(message)
      log.warning(s"received $message in disconnected state, adding to stash")
      context.become(receiveWhenConnecting(stash.enqueue(segment)))
  }

  // TODO implement buffering in case connection is lost
  //  var last: Option[TcpPackage[Outgoing]] = None

  def receiveWhenConnected(pipeline: ActorRef,
                           init: Init[WithinActorContext, TcpPackage[Out], TcpPackage[In]],
                           buffer: ByteString = ByteString.empty): Receive = {

    {
      case init.Event(TcpPackage(correlationId, message: In, _)) => message match {
        case HeartbeatRequestCommand => pipeline ! init.Command(TcpPackage(correlationId, HeartbeatResponseCommand, Some(AuthData("admin", "changeit"))))
        case _ =>
          val actor = binding.byX(correlationId) match {
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


      case x: ConnectionClosed ⇒
        log.warning(x.toString)
        log.info("Reconnecting")
        connect()

        context.become(receiveWhenConnecting())
    }
  }

  def tcpPackage(message: Out) = {
    val correlationId = binding.byY(sender).getOrElse {
      val x = newUuid
      log.info(s"add sender $sender for $x")
      binding = binding.+(x, sender)
      x
    }

    TcpPackage(correlationId, message, Some(AuthData("admin", "changeit")))
  }
}