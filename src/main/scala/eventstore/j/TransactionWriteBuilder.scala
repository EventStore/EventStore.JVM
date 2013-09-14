package eventstore
package j

import java.lang.Iterable
import Builder._

/**
 * @author Yaroslav Klymko
 */
class TransactionWriteBuilder(transactionId: Long) extends Builder[TransactionWrite]
    with RequireMasterSnippet[TransactionWriteBuilder]
    with EventDataSnippet[TransactionWriteBuilder] {

  def addEvent(x: EventData) = eventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]) = eventDataSnippet.addEvents(xs)
  def event(x: EventData) = eventDataSnippet.event(x)
  def events(xs: Iterable[EventData]) = eventDataSnippet.events(xs)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = TransactionWrite(
    transactionId = transactionId,
    events = Seq(eventDataSnippet.value: _*),
    requireMaster = requireMasterSnippet.value)
}
