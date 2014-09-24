package eventstore
package j

import java.lang.Iterable
import Builder._

class TransactionWriteBuilder(transactionId: Long) extends Builder[TransactionWrite]
    with RequireMasterSnippet[TransactionWriteBuilder]
    with EventDataSnippet[TransactionWriteBuilder] {

  def addEvent(x: EventData): TransactionWriteBuilder = EventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]): TransactionWriteBuilder = EventDataSnippet.addEvents(xs)
  def event(x: EventData): TransactionWriteBuilder = EventDataSnippet.event(x)
  def events(xs: Iterable[EventData]): TransactionWriteBuilder = EventDataSnippet.events(xs)

  override def performOnAnyNode: TransactionWriteBuilder = super.performOnAnyNode
  override def performOnMasterOnly: TransactionWriteBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): TransactionWriteBuilder = super.requireMaster(x)

  def build: TransactionWrite = TransactionWrite(
    transactionId = transactionId,
    events = EventDataSnippet.value.toList,
    requireMaster = _requireMaster)
}
