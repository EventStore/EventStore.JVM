package eventstore.examples

import akka.actor.ActorSystem
import eventstore.TransactionActor._
import eventstore.tcp.ConnectionActor
import eventstore.{ EventData, TransactionActor }

object ContinueTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())

  val transactionId = 0L
  val kickoff = Continue(transactionId)
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff))

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}