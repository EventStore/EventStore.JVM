package eventstore
package akka

import scala.concurrent.{ ExecutionContext, Future }
import _root_.akka.actor.ActorRef
import _root_.akka.pattern.ask
import _root_.akka.util.Timeout

trait EsTransaction {
  def transactionId: Long
  def write(events: List[EventData]): Future[Unit]
  def commit(): Future[Unit]
}

object EsTransaction {
  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def start(actor: ActorRef)(implicit timeout: Timeout): Future[EsTransaction] = {
    import TransactionActor.{ TransactionId, GetTransactionId }
    val future = actor ? GetTransactionId
    future.mapTo[TransactionId].map(x => continue(x.value, actor))
  }

  def continue(transactionId: Long, actor: ActorRef)(implicit timeout: Timeout): EsTransaction =
    EsTransactionForActor(transactionId, actor)
}

case class EsTransactionForActor(transactionId: Long, actor: ActorRef)(implicit timeout: Timeout) extends EsTransaction {

  import TransactionActor._
  import EsTransaction.executionContext

  def write(events: List[EventData]): Future[Unit] = {
    val future = actor ? Write(events)
    future.map(_ => ())
  }

  def commit(): Future[Unit] = {
    val future = actor ? Commit
    future.map(_ => ())
  }
}