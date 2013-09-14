package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsBuilder(streamId: String) extends Builder[ReadStreamEvents]
    with MaxCountSnippet[ReadStreamEventsBuilder]
    with DirectionSnippet[ReadStreamEventsBuilder]
    with RequireMasterSnippet[ReadStreamEventsBuilder]
    with ResolveLinkTosSnippet[ReadStreamEventsBuilder] {

  protected val _streamId = EventStream(streamId)
  protected var _fromNumber: EventNumber = EventNumber.First

  def fromNumber(x: Int) = set {
    _fromNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def fromFirst = set {
    _fromNumber = EventNumber.First
  }

  def fromLast = set {
    _fromNumber = EventNumber.Last
    backward
  }

  def maxCount(x: Int) = maxCountSnippet.maxCount(x)

  def forward = directionSnippet.forward
  def backward = directionSnippet.backward

  def resolveLinkTos(x: Boolean) = resolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = ReadStreamEvents(
    streamId = _streamId,
    fromNumber = _fromNumber,
    maxCount = maxCountSnippet.value,
    direction = directionSnippet.value,
    resolveLinkTos = resolveLinkTosSnippet.value,
    requireMaster = requireMasterSnippet.value)
}