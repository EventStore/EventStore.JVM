package eventstore

import akka.actor.{ Props, ActorRef, Actor }

/**
 * @author Yaroslav Klymko
 */

object Transaction {
  case class Write(events: Seq[EventData])
  case object WriteCompleted

  case object Commit
  case object CommitCompleted
}

object TransactionActor {
  def props(connection: ActorRef, client: ActorRef, transactionId: Long, requireMaster: Boolean = true): Props =
    Props(classOf[TransactionActor], connection, client, transactionId, requireMaster)
}

class TransactionActor(
    connection: ActorRef,
    client: ActorRef,
    transactionId: Long,
    requireMaster: Boolean) extends Actor {

  import Transaction._

  context watch connection
  context watch client

  val receiveWriteCompleted: Receive = {
    case TransactionWriteCompleted(`transactionId`) => client ! WriteCompleted
  }

  val receiveCommitCompleted: Receive = {
    case TransactionCommitCompleted(`transactionId`) =>
      client ! CommitCompleted
      context stop self
  }

  def receive = receiveWriteCompleted orElse {
    case Write(events) => connection ! TransactionWrite(transactionId, events, requireMaster)
    case Commit =>
      connection ! TransactionCommit(transactionId, requireMaster)
      context become (receiveWriteCompleted orElse receiveCommitCompleted)
  }
}
