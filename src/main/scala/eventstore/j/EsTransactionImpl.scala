package eventstore.j

import java.util
import scala.collection.JavaConverters._
import eventstore.EventData

class EsTransactionImpl(transaction: eventstore.EsTransaction) extends EsTransaction {
  def getId = transaction.transactionId
  def write(events: util.Collection[EventData]) = transaction.write(events.asScala.toList)
  def commit() = transaction.commit()
}