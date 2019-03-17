package eventstore
package akka

import scala.collection.immutable.Queue
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{Actor, ActorLogging, ActorRef, Props}


object TransactionActor {

  def props(
    connection:    ActorRef,
    kickoff:       Kickoff,
    requireMaster: Boolean                 = Settings.Default.requireMaster,
    credentials:   Option[UserCredentials] = None
  ): Props =
    Props(classOf[TransactionActor], connection, kickoff, requireMaster, credentials)

  sealed trait Kickoff
  @SerialVersionUID(1L) case class Start(data: TransactionStart) extends Kickoff
  @SerialVersionUID(1L) case class Continue(transactionId: Long) extends Kickoff

  @SerialVersionUID(1L) case object GetTransactionId
  @SerialVersionUID(1L) case class TransactionId(value: Long)

  sealed trait Command

  @SerialVersionUID(1L) case class Write(events: List[EventData]) extends Command

  object Write {
    def apply(event: EventData, events: EventData*): Write = Write(event :: events.toList)
  }

  @SerialVersionUID(1L) case object WriteCompleted

  @SerialVersionUID(1L) case object Commit extends Command
  @SerialVersionUID(1L) case class CommitCompleted(
    range:    Option[EventNumber.Range] = None,
    position: Option[Position.Exact]    = None
  )

  /**
   * Java API
   */
  def getProps(
    connection:    ActorRef,
    kickoff:       Kickoff,
    requireMaster: Boolean,
    credentials:   Option[UserCredentials]
  ): Props = props(connection, kickoff, requireMaster, credentials)

  /**
   * Java API
   */
  def getProps(connection: ActorRef, kickoff: Kickoff): Props = props(connection, kickoff)

  /**
   * Java API
   */
  def start(data: TransactionStart): Start = Start(data)

  /**
   * Java API
   */
  def continue(transactionId: Long): Continue = Continue(transactionId)

  /**
   * Java API
   */
  def getTransactionId: GetTransactionId.type = GetTransactionId

  /**
   * Java API
   */
  def transactionId(value: Long): TransactionId = TransactionId(value)

  /**
   * Java API
   */
  def write(events: List[EventData]): Write = Write(events)

  /**
   * Java API
   */
  def writeCompleted: WriteCompleted.type = WriteCompleted

  /**
   * Java API
   */
  def commit: Commit.type = Commit

  /**
   * Java API
   */
  def commitCompleted: CommitCompleted.type = CommitCompleted
}

class TransactionActor(
    connection:    ActorRef,
    kickoff:       TransactionActor.Kickoff,
    requireMaster: Boolean,
    credentials:   Option[UserCredentials]
) extends Actor with ActorLogging {
  import TransactionActor._

  context watch connection

  def receive: Receive = kickoff match {
    case Continue(transactionId) => new ContinueReceive(transactionId).apply(Queue())
    case Start(transactionStart) =>
      toConnection(transactionStart)
      starting(Queue(), Queue())
  }

  def starting(stash: Queue[StashEntry], awaitingId: Queue[ActorRef]): Receive = {
    case x: Command       => context become starting(stash enqueue StashEntry(x), awaitingId)
    case GetTransactionId => context become starting(stash, awaitingId enqueue sender())
    case TransactionStartCompleted(transactionId) =>
      awaitingId.foreach(_ ! TransactionId(transactionId))
      context become new ContinueReceive(transactionId).apply(stash)

    case failure @ Failure(error) =>
      awaitingId.foreach(_ ! failure)
      throw error
  }

  class ContinueReceive(transactionId: Long) extends (Queue[StashEntry] => Receive) {
    val common: Receive = {
      case GetTransactionId => sender() ! TransactionId(transactionId)
    }

    val empty: Receive = common or {
      case Commit        => context become commit(sender())
      case Write(events) => context become write(events, sender(), Queue())
    }

    def failure(client: ActorRef): Receive = {
      case failure: Failure =>
        client ! failure
        context stop self
    }

    def commit(client: ActorRef): Receive = {
      toConnection(TransactionCommit(transactionId, requireMaster))
      common or failure(client) or {
        case TransactionCommitCompleted(`transactionId`, range, position) =>
          client ! CommitCompleted(range, position)
          context stop self
      }
    }

    def write(events: List[EventData], client: ActorRef, stash: Queue[StashEntry]): Receive = {
      toConnection(TransactionWrite(transactionId, events, requireMaster))
      writing(client, stash)
    }

    def writing(client: ActorRef, stash: Queue[StashEntry]): Receive = common or failure(client) or {
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

  def toConnection(x: Out): Unit = {
    connection ! credentials.fold[OutLike](x)(x.withCredentials)
  }

  @SerialVersionUID(1L) case class StashEntry(command: Command, client: ActorRef = sender())
}