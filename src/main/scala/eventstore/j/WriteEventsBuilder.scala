package eventstore
package j

import Builder._
import java.lang.Iterable

/**
 * @author Yaroslav Klymko
 */
class WriteEventsBuilder(streamId: String) extends Builder[WriteEvents]
    with EventDataSnippet[WriteEventsBuilder]
    with ExpectVersionSnippet[WriteEventsBuilder]
    with RequireMasterSnippet[WriteEventsBuilder] {

  protected val _streamId: EventStream.Id = EventStream(streamId)

  def addEvent(x: EventData) = eventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]) = eventDataSnippet.addEvents(xs)
  def event(x: EventData) = eventDataSnippet.event(x)
  def events(xs: Iterable[EventData]) = eventDataSnippet.events(xs)

  def expectNoStream = expectedVersionSnippet.expectNoStream
  def expectAnyVersion = expectedVersionSnippet.expectAnyVersion
  def expectVersion(x: Int) = expectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build: WriteEvents = WriteEvents(
    streamId = _streamId,
    events = Seq(eventDataSnippet.value: _*),
    expectedVersion = expectedVersionSnippet.value,
    requireMaster = requireMasterSnippet.value)
}
