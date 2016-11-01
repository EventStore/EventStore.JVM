package eventstore
package j

import Builder._

class ReadEventBuilder(streamId: String) extends Builder[ReadEvent]
    with ResolveLinkTosSnippet[ReadEventBuilder]
    with RequireMasterSnippet[ReadEventBuilder] {

  protected val _streamId = EventStream.Id(streamId)
  protected var _eventNumber: EventNumber = EventNumber.First

  def number(x: EventNumber): ReadEventBuilder = set {
    _eventNumber = x
  }

  def number(x: Int): ReadEventBuilder = number(if (x < 0) EventNumber.Last else EventNumber(x))

  def first: ReadEventBuilder = number(EventNumber.First)

  def last: ReadEventBuilder = number(EventNumber.Last)

  override def resolveLinkTos(x: Boolean): ReadEventBuilder = super.resolveLinkTos(x)

  override def performOnAnyNode: ReadEventBuilder = super.performOnAnyNode
  override def performOnMasterOnly: ReadEventBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): ReadEventBuilder = super.requireMaster(x)

  def build: ReadEvent = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster
  )
}