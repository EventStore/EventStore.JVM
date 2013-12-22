package eventstore
package j

import java.lang.Iterable
import Builder._

class TransactionWriteBuilder(transactionId: Long) extends Builder[TransactionWrite]
    with RequireMasterSnippet[TransactionWriteBuilder]
    with EventDataSnippet[TransactionWriteBuilder] {

  def addEvent(x: EventData) = EventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]) = EventDataSnippet.addEvents(xs)
  def event(x: EventData) = EventDataSnippet.event(x)
  def events(xs: Iterable[EventData]) = EventDataSnippet.events(xs)

  def requireMaster(x: Boolean) = RequireMasterSnippet.requireMaster(x)

  def build = TransactionWrite(
    transactionId = transactionId,
    events = Seq(EventDataSnippet.value: _*),
    requireMaster = RequireMasterSnippet.value)
}
