package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
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

  def maxCount(x: Int) = maxCountSnippet.maxCount(x)

  def forward = directionSnippet.forward
  def backward = directionSnippet.backward

  def resolveLinkTos(x: Boolean) = resolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = ReadAllEvents(
    fromPosition = _fromPosition,
    maxCount = maxCountSnippet.value,
    direction = directionSnippet.value,
    resolveLinkTos = resolveLinkTosSnippet.value,
    requireMaster = requireMasterSnippet.value)
}
