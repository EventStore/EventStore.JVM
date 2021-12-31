package eventstore
package akka
package examples

import _root_.akka.actor.{ Props, ActorSystem }
import eventstore.core.util.uuid.randomUuid
import eventstore.akka.tcp.ConnectionActor
import eventstore.akka.TransactionActor._
import _root_.akka.actor.ActorRef

object ContinueTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")

  val transactionId = 0L
  val kickoff = Continue(transactionId)
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff), "transaction")
  implicit val transactionResult: ActorRef = system.actorOf(Props[TransactionResult](), "result")

  val data = EventData("transaction-event", randomUuid)

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}