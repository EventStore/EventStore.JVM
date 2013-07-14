package eventstore.client.examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._


import scala.util.Random
import eventstore.client._
import eventstore.client.OperationResult._
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import eventstore.client.TransactionWrite
import eventstore.client.TransactionCommit
import eventstore.client.TransactionCommitCompleted
import eventstore.client.DeleteStream
import eventstore.client.NewEvent
import eventstore.client.TransactionWriteCompleted
import eventstore.client.TransactionStart
import eventstore.client.CreateStream
import eventstore.client.CreateStreamCompleted
import scala.Some
import eventstore.client.TransactionStartCompleted
import eventstore.client.tcp.UuidSerializer


/**
 * @author Yaroslav Klymko
 */
class TransactionCommitActor extends Actor with ActorLogging {

  import context.dispatcher

  //  val subscribeToStream = SubscribeToStream(testStreamId, resolveLinkTos = false)
  val streamId = newUuid.toString

  def eventId = UuidSerializer.serialize(newUuid)

  def newEvent = NewEvent(eventId, Some("test"), isJson = false, ByteString.empty, None)


  def receive = {
    case _: Tcp.Connected =>

      sender ! CreateStream(
        streamId,
        newUuid,
//        ByteString("la"),
        eventId,
        allowForwarding = true,
        isJson = false)

//      sender ! TransactionStart(testStreamId, 0, allowForwarding = false)

      /*context.become {
        case CreateStreamCompleted(Success, _) =>
          sender ! ScavengeDatabase

          sender ! TransactionStart(streamId, 0, allowForwarding = false)

          context.become {
            case TransactionStartCompleted(transactionId, Success, _) =>
              sender ! TransactionWrite(transactionId, List(newEvent, newEvent, newEvent), allowForwarding = false)

              context.become {
                case x: TransactionWriteCompleted =>
                  sender ! TransactionCommit(transactionId, allowForwarding = false)

                case TransactionCommitCompleted(_, Success, _) =>
                  sender ! DeleteStream(streamId, 3, allowForwarding = false)

                case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand
              }

            case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand
          }

        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x => log.warning(x.toString)
      }*/
  }
}
