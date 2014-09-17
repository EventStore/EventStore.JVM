package eventstore
package j

import Builder._
import java.lang.Iterable

class WriteEventsBuilder(streamId: String) extends Builder[WriteEvents]
    with EventDataSnippet[WriteEventsBuilder]
    with ExpectVersionSnippet[WriteEventsBuilder]
    with RequireMasterSnippet[WriteEventsBuilder] {

  protected val _streamId: EventStream.Id = EventStream.Id(streamId)

  def addEvent(x: EventData): WriteEventsBuilder = EventDataSnippet.addEvent(x)
  def addEvents(xs: Iterable[EventData]): WriteEventsBuilder = EventDataSnippet.addEvents(xs)
  def event(x: EventData): WriteEventsBuilder = EventDataSnippet.event(x)
  def events(xs: Iterable[EventData]): WriteEventsBuilder = EventDataSnippet.events(xs)

  def expectNoStream: WriteEventsBuilder = ExpectedVersionSnippet.expectNoStream
  def expectAnyVersion: WriteEventsBuilder = ExpectedVersionSnippet.expectAnyVersion
  def expectVersion(x: Int): WriteEventsBuilder = ExpectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean): WriteEventsBuilder = RequireMasterSnippet.requireMaster(x)

  def build: WriteEvents = WriteEvents(
    streamId = _streamId,
    events = EventDataSnippet.value.toList,
    expectedVersion = ExpectedVersionSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}
