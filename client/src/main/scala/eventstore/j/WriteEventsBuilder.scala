package eventstore
package j

import Builder._
import java.lang.Iterable

class WriteEventsBuilder(streamId: String) extends Builder[WriteEvents]
    with EventDataSnippet[WriteEventsBuilder]
    with ExpectVersionSnippet[WriteEventsBuilder]
    with RequireMasterSnippet[WriteEventsBuilder] {

  protected val _streamId: EventStream.Id = EventStream.Id(streamId)

  override def addEvent(x: EventData): WriteEventsBuilder = super.addEvent(x)
  override def addEvents(xs: Iterable[EventData]): WriteEventsBuilder = super.addEvents(xs)
  override def event(x: EventData): WriteEventsBuilder = super.event(x)
  override def events(xs: Iterable[EventData]): WriteEventsBuilder = super.events(xs)

  override def expectNoStream: WriteEventsBuilder = super.expectNoStream
  override def expectAnyVersion: WriteEventsBuilder = super.expectAnyVersion
  override def expectVersion(x: Long): WriteEventsBuilder = super.expectVersion(x)
  override def expectVersion(x: ExpectedVersion): WriteEventsBuilder = super.expectVersion(x)

  override def performOnAnyNode: WriteEventsBuilder = super.performOnAnyNode
  override def performOnMasterOnly: WriteEventsBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): WriteEventsBuilder = super.requireMaster(x)

  def build: WriteEvents = WriteEvents(
    streamId = _streamId,
    events = _events.toList,
    expectedVersion = _expectVersion,
    requireMaster = _requireMaster
  )
}
