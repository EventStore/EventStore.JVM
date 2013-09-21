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

  def addEvent(x: EventData) = EventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]) = EventDataSnippet.addEvents(xs)
  def event(x: EventData) = EventDataSnippet.event(x)
  def events(xs: Iterable[EventData]) = EventDataSnippet.events(xs)

  def expectNoStream = ExpectedVersionSnippet.expectNoStream
  def expectAnyVersion = ExpectedVersionSnippet.expectAnyVersion
  def expectVersion(x: Int) = ExpectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean) = RequireMasterSnippet.requireMaster(x)

  def build: WriteEvents = WriteEvents(
    streamId = _streamId,
    events = Seq(EventDataSnippet.value: _*),
    expectedVersion = ExpectedVersionSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}
