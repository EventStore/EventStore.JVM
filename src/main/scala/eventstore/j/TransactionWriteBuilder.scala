package eventstore
package j

import java.lang.Iterable
import Builder._

class TransactionWriteBuilder(transactionId: Long) extends Builder[TransactionWrite]
    with RequireMasterSnippet[TransactionWriteBuilder]
    with EventDataSnippet[TransactionWriteBuilder] {

  override def addEvent(x: EventData): TransactionWriteBuilder = super.addEvent(x)
  override def addEvents(xs: Iterable[EventData]): TransactionWriteBuilder = super.addEvents(xs)
  override def event(x: EventData): TransactionWriteBuilder = super.event(x)
  override def events(xs: Iterable[EventData]): TransactionWriteBuilder = super.events(xs)

  override def performOnAnyNode: TransactionWriteBuilder = super.performOnAnyNode
  override def performOnMasterOnly: TransactionWriteBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): TransactionWriteBuilder = super.requireMaster(x)

  def build: TransactionWrite = TransactionWrite(
    transactionId = transactionId,
    events = _events.toList,
    requireMaster = _requireMaster
  )
}
