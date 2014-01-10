package eventstore
package j

import Builder._

class ReadEventBuilder(streamId: String) extends Builder[ReadEvent]
    with ResolveLinkTosSnippet[ReadEventBuilder]
    with RequireMasterSnippet[ReadEventBuilder] {

  protected val _streamId = EventStream(streamId)
  protected var _eventNumber: EventNumber = EventNumber.First

  def number(x: EventNumber): ReadEventBuilder = set {
    _eventNumber = x
  }

  def number(x: Int): ReadEventBuilder = number(if (x < 0) EventNumber.Last else EventNumber(x))

  def first: ReadEventBuilder = number(EventNumber.First)

  def last: ReadEventBuilder = number(EventNumber.Last)

  def resolveLinkTos(x: Boolean): ReadEventBuilder = ResolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean): ReadEventBuilder = RequireMasterSnippet.requireMaster(x)

  def build: ReadEvent = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = ResolveLinkTosSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}