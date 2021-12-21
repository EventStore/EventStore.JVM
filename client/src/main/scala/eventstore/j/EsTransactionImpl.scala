package eventstore.j

import java.util
import scala.concurrent.Future
import eventstore.EventData
import eventstore.core.ScalaCompat.JavaConverters._

class EsTransactionImpl(transaction: eventstore.akka.EsTransaction) extends EsTransaction {
  def getId: Long = transaction.transactionId
  def write(events: util.Collection[EventData]): Future[Unit] = transaction.write(events.asScala.toList)
  def commit(): Future[Unit] = transaction.commit()
}