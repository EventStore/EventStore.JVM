package eventstore
package j

import Builder._

class ReadAllEventsBuilder extends Builder[ReadAllEvents]
    with MaxCountSnippet[ReadAllEventsBuilder]
    with DirectionSnippet[ReadAllEventsBuilder]
    with ResolveLinkTosSnippet[ReadAllEventsBuilder]
    with RequireMasterSnippet[ReadAllEventsBuilder] {

  private var _fromPosition: Position = Position.First

  def fromPosition(x: Position): ReadAllEventsBuilder = set {
    _fromPosition = x
  }

  def fromPosition(commitPosition: Long, preparePosition: Long): ReadAllEventsBuilder =
    fromPosition(Position(commitPosition, preparePosition))

  def fromFirst: ReadAllEventsBuilder = fromPosition(Position.First)

  def fromLast: ReadAllEventsBuilder = fromPosition(Position.Last)

  override def maxCount(x: Int): ReadAllEventsBuilder = super.maxCount(x)

  override def forward: ReadAllEventsBuilder = super.forward
  override def backward: ReadAllEventsBuilder = super.backward

  override def resolveLinkTos(x: Boolean): ReadAllEventsBuilder = super.resolveLinkTos(x)

  override def performOnAnyNode: ReadAllEventsBuilder = super.performOnAnyNode
  override def performOnMasterOnly: ReadAllEventsBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): ReadAllEventsBuilder = super.requireMaster(x)

  def build: ReadAllEvents = ReadAllEvents(
    fromPosition = _fromPosition,
    maxCount = _maxCount,
    direction = _direction,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster
  )
}
