package eventstore
package j

/**
 * @author Yaroslav Klymko
 */
class ReadEventBuilder(streamId: String, eventNumber: Int) extends Builder[ReadEvent] {
  private val _streamId = EventStream(streamId)
  private val _eventNumber = if (eventNumber < 0) EventNumber.Last else EventNumber(eventNumber) // TODO
  private var _resolveLinkTos = false
  private var _requireMaster: Boolean = true

  def resolveLinkTos(x: Boolean) = set {
    _resolveLinkTos = x
  }

  def requireMaster(x: Boolean) = set {
    _requireMaster = x
  }

  def build = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster)
}
