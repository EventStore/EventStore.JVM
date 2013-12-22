package eventstore

import akka.actor.{ ActorLogging, Props, ActorRef, Actor }
import scala.collection.immutable.Queue
import akka.actor.Status.Failure

/**
 * @author Yaroslav Klymko
 */
object TransactionActor {

  sealed trait Kickoff
  case class Start(data: TransactionStart) extends Kickoff
  case class Continue(transactionId: Long) extends Kickoff

  case object GetTransactionId
  case class TransactionId(value: Long)

  sealed trait Command

  case class Write(events: Seq[EventData]) extends Command

  object Write {
    def apply(event: EventData, events: EventData*): Write = Write(Seq(event) ++ Seq(events: _*)) // TODO
  }

  case object WriteCompleted

  case object Commit extends Command
  case object CommitCompleted

  def props(connection: ActorRef, kickoff: Kickoff, requireMaster: Boolean = true): Props =
    Props(classOf[TransactionActor], connection, kickoff, requireMaster)
}

class TransactionActor(
    connection: ActorRef,
    kickoff: TransactionActor.Kickoff,
    requireMaster: Boolean /*, credentials: Option[UserCredentials]TODO*/ ) extends Actor with ActorLogging {
  import TransactionActor._

  context watch connection

  def receive: Receive = kickoff match {
    case Continue(transactionId) => new ContinueReceive(transactionId).apply(Queue())
    case Start(transactionStart) =>
      connection ! transactionStart
      starting(Queue(), Queue())
  }

  def starting(stash: Queue[StashEntry], awaitingId: Queue[ActorRef]): Receive = {
    case x: Command       => context become starting(stash enqueue StashEntry(x), awaitingId)
    case GetTransactionId => context become starting(stash, awaitingId enqueue sender)
    case TransactionStartCompleted(transactionId) =>
      awaitingId.foreach(_ ! TransactionId(transactionId))
      context become new ContinueReceive(transactionId).apply(stash)
    case failure @ Failure(error) =>
      awaitingId.foreach(_ ! failure)
      throw error
  }

  class ContinueReceive(transactionId: Long) extends (Queue[StashEntry] => Receive) {
    val common: Receive = {
      case GetTransactionId => sender ! TransactionId(transactionId)
    }

    val empty: Receive = common orElse {
      case Commit        => context become commit(sender)
      case Write(events) => context become write(events, sender, Queue())
    }

    def failure(client: ActorRef): Receive = {
      case failure @ Failure(error) =>
        client ! failure
        throw error
    }

    def commit(client: ActorRef): Receive = {
      connection ! TransactionCommit(transactionId, requireMaster)
      common orElse failure(client) orElse {
        case TransactionCommitCompleted(`transactionId`) =>
          client ! CommitCompleted
          context stop self
      }
    }

    def write(events: Seq[EventData], client: ActorRef, stash: Queue[StashEntry]): Receive = {
      connection ! TransactionWrite(transactionId, events, requireMaster)
      writing(client, stash)
    }

    def writing(client: ActorRef, stash: Queue[StashEntry]): Receive = common orElse failure(client) orElse {
      case x: Command => context become writing(client, stash enqueue StashEntry(x))
      case TransactionWriteCompleted(`transactionId`) =>
        client ! WriteCompleted
        context become apply(stash)
    }

    def apply(stash: Queue[StashEntry]): Receive =
      if (stash.isEmpty) empty
      else {
        val (StashEntry(command, client), tail) = stash.dequeue
        command match {
          case Commit        => commit(client)
          case Write(events) => write(events, client, tail)
        }
      }
  }

  case class StashEntry(command: Command, client: ActorRef = sender)
}