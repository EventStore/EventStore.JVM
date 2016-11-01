package eventstore
package j

import Builder._

class ReadStreamEventsBuilder(streamId: String) extends Builder[ReadStreamEvents]
    with MaxCountSnippet[ReadStreamEventsBuilder]
    with DirectionSnippet[ReadStreamEventsBuilder]
    with RequireMasterSnippet[ReadStreamEventsBuilder]
    with ResolveLinkTosSnippet[ReadStreamEventsBuilder] {

  protected val _streamId = EventStream.Id(streamId)
  protected var _fromNumber: EventNumber = EventNumber.First

  def fromNumber(x: EventNumber): ReadStreamEventsBuilder = set {
    _fromNumber = x
  }

  def fromNumber(x: Int): ReadStreamEventsBuilder = fromNumber(if (x < 0) EventNumber.Last else EventNumber(x))

  def fromFirst: ReadStreamEventsBuilder = fromNumber(EventNumber.First)

  def fromLast: ReadStreamEventsBuilder = fromNumber(EventNumber.Last)

  override def maxCount(x: Int): ReadStreamEventsBuilder = super.maxCount(x)

  override def forward: ReadStreamEventsBuilder = super.forward
  override def backward: ReadStreamEventsBuilder = super.backward

  override def resolveLinkTos(x: Boolean): ReadStreamEventsBuilder = super.resolveLinkTos(x)

  override def performOnAnyNode: ReadStreamEventsBuilder = super.performOnAnyNode
  override def performOnMasterOnly: ReadStreamEventsBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): ReadStreamEventsBuilder = super.requireMaster(x)

  def build: ReadStreamEvents = ReadStreamEvents(
    streamId = _streamId,
    fromNumber = _fromNumber,
    maxCount = _maxCount,
    direction = _direction,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster
  )
}