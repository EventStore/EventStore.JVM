package eventstore.client
package tcp

import java.net.InetSocketAddress
import akka.actor.{Stash, ActorLogging, Actor, ActorRef}
import akka.io.{IO, Tcp}

import scala.collection.immutable.Queue


/**
 * @author Yaroslav Klymko
 */
class ConnectionActor(address: InetSocketAddress) extends Actor with ActorLogging {
  import ConnectionActor._

  import Tcp._
  import context.system
  import context.dispatcher

//  var state = ConnectingState(Queue())

  connect

  def connect = IO(Tcp) ! Connect(address)


  var binding = new BidirectionalMap()
  // todo clean on expiry or somehow

  def receive = receiveWhenConnecting()

  def receiveWhenConnecting(stash: Queue[TcpPackage[Out]] = Queue()): Receive = {
    case connected@Connected(remote, local) =>
      log.info(s"Connected to $remote from $local")

      val connection = sender
      connection ! Register(self/*, useResumeWriting = false*/)

      stash.foreach(segment => connection ! Write(serialize(segment)))

      context.become(receiveWhenConnected(connection))

    case command@CommandFailed(_: Connect) =>
      log.warning(command.toString)
      context stop self


    case message: Out =>

      val correlationId = binding.byChannel(sender).getOrElse {
        val x = newUuid
        log.info(s"add sender for $x")
        binding = binding.+(x, sender)
        x
      }

      val segment = TcpPackage(correlationId, message)
      log.warning(s"received $message in disconnected state, adding to stash")
      context.become(receiveWhenConnecting(stash.enqueue(segment)))

    case x => println(x)
  }

  // TODO implement buffering in case connection is lost
  //  var last: Option[TcpPackage[Outgoing]] = None

  def receiveWhenConnected(connection: ActorRef, buffer: ByteString = ByteString.empty): Receive = {
//    var buffer = ByteString.empty

    def onSegment(segment: TcpPackage[In]) {
      segment.message match {
        case HeartbeatRequestCommand => self ! HeartbeatResponseCommand // TODO add UUI
        case _ =>
          val actor = binding.byCorrelationId(segment.correlationId) match {
            case Some(channel) =>  channel
            case None => log.warning(s"sender not found for $segment")
              Actor.noSender
          }
          actor ! segment.message
      }
    }





    {
      case message: Out =>

        val correlationId = binding.byChannel(sender).getOrElse {
          val x = newUuid
          log.info(s"add sender for $x")
          binding = binding.+(x, sender)
          x
        }

        val segment = TcpPackage(correlationId, message)

        val data = serialize(segment)

        connection ! Write(data)
//        last = Some(segment)
      //          log.info(s"<< $message")

      case Received(data) =>
        context.become(receiveWhenConnected(connection, Segment.deserialize(buffer ++ data, onSegment)))

      case x: ConnectionClosed ⇒
        log.warning(x.toString)
        connect

//        val stash = last.fold[Queue[TcpPackage[Outgoing]]](Queue())(Queue(_))
//        context.become(receiveWhenConnecting(stash))
        context.become(receiveWhenConnecting())

      case x => println(x)

    }
  }

  def serialize(segment:TcpPackage[Out]):ByteString = {
    val bytes = segment.serialize

    ByteString(Segment.sizeToBytes(bytes.length) ++ bytes)
  }
}

object ConnectionActor{

  case object Ack extends Tcp.Event
//  sealed trait State
//
//  case class ConnectedState() extends State
//
//  case class ConnectingState(stash: Queue[TcpPackage]) extends State
}

case class BidirectionalMap(xy: Map[Uuid, ActorRef] = Map(),
                            yx: Map[ActorRef, Uuid] = Map()){


  def +(x: Uuid, y: ActorRef) = {
    if ((xy contains x) && (yx contains y)) this
    else copy(xy = xy + (x -> y), yx = yx + (y -> x))
  }

  def -(correlationId: Uuid) = ???

  def -(channel: ActorRef) = ???

  def byCorrelationId(correlationId: Uuid) = xy.get(correlationId)

  def byChannel(channel: ActorRef) = yx.get(channel)
}