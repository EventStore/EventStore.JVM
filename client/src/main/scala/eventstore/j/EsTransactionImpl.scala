package eventstore.j

import java.util
import eventstore.EventData
import eventstore.core.ScalaCompat._

class EsTransactionImpl(transaction: eventstore.akka.EsTransaction) extends EsTransaction {
  def getId = transaction.transactionId
  def write(events: util.Collection[EventData]) = transaction.write(events.toScala)
  def commit() = transaction.commit()
}