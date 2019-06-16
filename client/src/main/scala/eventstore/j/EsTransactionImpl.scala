package eventstore.j

import java.util
import eventstore.EventData
import eventstore.core.ScalaCompat.JavaConverters._

class EsTransactionImpl(transaction: eventstore.akka.EsTransaction) extends EsTransaction {
  def getId = transaction.transactionId
  def write(events: util.Collection[EventData]) = transaction.write(events.asScala.toList)
  def commit() = transaction.commit()
}