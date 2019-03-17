package eventstore
package akka
package examples

import _root_.akka.actor.{ Props, ActorSystem }
import eventstore.akka.tcp.ConnectionActor
import eventstore.akka.TransactionActor._

object StartTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")

  val kickoff = Start(TransactionStart(EventStream.Id("my-stream")))
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff), "transaction")
  implicit val transactionResult = system.actorOf(Props[TransactionResult], "result")

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}