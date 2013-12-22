package eventstore
package j

import Builder._

class ReadAllEventsBuilder extends Builder[ReadAllEvents]
    with MaxCountSnippet[ReadAllEventsBuilder]
    with DirectionSnippet[ReadAllEventsBuilder]
    with ResolveLinkTosSnippet[ReadAllEventsBuilder]
    with RequireMasterSnippet[ReadAllEventsBuilder] {

  private var _fromPosition: Position = Position.First

  def fromFirstPosition = set {
    _fromPosition = Position.First
  }

  def fromLastPosition = set {
    _fromPosition = Position.Last
    backward
  }

  def fromPosition(commitPosition: Long, preparePosition: Long) = set {
    _fromPosition = Position(commitPosition, preparePosition)
  }

  def maxCount(x: Int) = MaxCountSnippet.maxCount(x)

  def forward = DirectionSnippet.forward
  def backward = DirectionSnippet.backward

  def resolveLinkTos(x: Boolean) = ResolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean) = RequireMasterSnippet.requireMaster(x)

  def build = ReadAllEvents(
    fromPosition = _fromPosition,
    maxCount = MaxCountSnippet.value,
    direction = DirectionSnippet.value,
    resolveLinkTos = ResolveLinkTosSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}
