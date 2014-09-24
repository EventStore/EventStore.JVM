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

  override def expectNoStream: WriteEventsBuilder = super.expectNoStream
  override def expectAnyVersion: WriteEventsBuilder = super.expectAnyVersion
  override def expectVersion(x: Int): WriteEventsBuilder = super.expectVersion(x)
  override def expectVersion(x: ExpectedVersion): WriteEventsBuilder = super.expectVersion(x)

  override def performOnAnyNode: WriteEventsBuilder = super.performOnAnyNode
  override def performOnMasterOnly: WriteEventsBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): WriteEventsBuilder = super.requireMaster(x)

  def build: WriteEvents = WriteEvents(
    streamId = _streamId,
    events = EventDataSnippet.value.toList,
    expectedVersion = _expectVersion,
    requireMaster = _requireMaster)
}
