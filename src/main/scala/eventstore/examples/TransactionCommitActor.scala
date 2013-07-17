package eventstore.examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._


import scala.util.Random
import eventstore._
import eventstore.OperationResult._
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import eventstore.TransactionWrite
import eventstore.TransactionCommit
import eventstore.TransactionCommitCompleted
import eventstore.DeleteStream
import eventstore.NewEvent
import eventstore.TransactionWriteCompleted
import eventstore.TransactionStart
import eventstore.CreateStream
import eventstore.CreateStreamCompleted
import scala.Some
import eventstore.TransactionStartCompleted
import eventstore.tcp.UuidSerializer


/**
 * @author Yaroslav Klymko
 */
class TransactionCommitActor extends Actor with ActorLogging {

  import context.dispatcher

  //  val subscribeToStream = SubscribeToStream(testStreamId, resolveLinkTos = false)
  val streamId = newUuid.toString

  def eventId = UuidSerializer.serialize(newUuid)

  def newEvent = NewEvent(eventId, "test", isJson = false, ByteString.empty, None)


  def receive = {
    case _: Tcp.Connected =>

      sender ! CreateStream(
        streamId,
        newUuid,
//        ByteString("la"),
        eventId,
        requireMaster = true,
        isJson = false)

//      sender ! TransactionStart(testStreamId, 0, requireMaster = false)

      /*context.become {
        case CreateStreamCompleted(Success, _) =>
          sender ! ScavengeDatabase

          sender ! TransactionStart(streamId, 0, requireMaster = false)

          context.become {
            case TransactionStartCompleted(transactionId, Success, _) =>
              sender ! TransactionWrite(transactionId, List(newEvent, newEvent, newEvent), requireMaster = false)

              context.become {
                case x: TransactionWriteCompleted =>
                  sender ! TransactionCommit(transactionId, requireMaster = false)

                case TransactionCommitCompleted(_, Success, _) =>
                  sender ! DeleteStream(streamId, 3, requireMaster = false)

                case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand
              }

            case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand
          }

        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x => log.warning(x.toString)
      }*/
  }
}
