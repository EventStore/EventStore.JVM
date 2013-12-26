package eventstore
package j

import Builder._

class ReadStreamEventsBuilder(streamId: String) extends Builder[ReadStreamEvents]
    with MaxCountSnippet[ReadStreamEventsBuilder]
    with DirectionSnippet[ReadStreamEventsBuilder]
    with RequireMasterSnippet[ReadStreamEventsBuilder]
    with ResolveLinkTosSnippet[ReadStreamEventsBuilder] {

  protected val _streamId = EventStream(streamId)
  protected var _fromNumber: EventNumber = EventNumber.First

  def fromNumber(x: Int): ReadStreamEventsBuilder = set {
    _fromNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def fromFirst: ReadStreamEventsBuilder = set {
    _fromNumber = EventNumber.First
  }

  def fromLast: ReadStreamEventsBuilder = set {
    _fromNumber = EventNumber.Last
    backward
  }

  def maxCount(x: Int): ReadStreamEventsBuilder = MaxCountSnippet.maxCount(x)

  def forward: ReadStreamEventsBuilder = DirectionSnippet.forward

  def backward: ReadStreamEventsBuilder = DirectionSnippet.backward

  def resolveLinkTos(x: Boolean): ReadStreamEventsBuilder = ResolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean): ReadStreamEventsBuilder = RequireMasterSnippet.requireMaster(x)

  def build: ReadStreamEvents = ReadStreamEvents(
    streamId = _streamId,
    fromNumber = _fromNumber,
    maxCount = MaxCountSnippet.value,
    direction = DirectionSnippet.value,
    resolveLinkTos = ResolveLinkTosSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}