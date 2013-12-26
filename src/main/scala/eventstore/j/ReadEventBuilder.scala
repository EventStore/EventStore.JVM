package eventstore
package j

import Builder._

class ReadEventBuilder(streamId: String) extends Builder[ReadEvent]
    with ResolveLinkTosSnippet[ReadEventBuilder]
    with RequireMasterSnippet[ReadEventBuilder] {

  protected val _streamId = EventStream(streamId)
  protected var _eventNumber: EventNumber = EventNumber.First

  def eventNumber(x: Int): ReadEventBuilder = set {
    _eventNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def eventNumberFirst: ReadEventBuilder = set {
    _eventNumber = EventNumber.First
  }

  def eventNumberLast: ReadEventBuilder = set {
    _eventNumber = EventNumber.Last
  }

  def resolveLinkTos(x: Boolean): ReadEventBuilder = ResolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean): ReadEventBuilder = RequireMasterSnippet.requireMaster(x)

  def build: ReadEvent = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = ResolveLinkTosSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}