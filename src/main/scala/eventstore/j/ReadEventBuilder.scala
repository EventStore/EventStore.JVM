package eventstore
package j

/**
 * @author Yaroslav Klymko
 */
class ReadEventBuilder(streamId: String) extends ReadBuilder[ReadEvent] {
  protected val _streamId = EventStream(streamId)
  protected var _eventNumber: EventNumber = EventNumber.First

  def eventNumber(x: Int) = set {
    _eventNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def eventNumberFirst = set {
    _eventNumber = EventNumber.First
  }

  def eventNumberLast = set {
    _eventNumber = EventNumber.Last
  }

  def build = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster)
}
