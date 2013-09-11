package eventstore
package j

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsBuilder(streamId: String) extends ReadBuilder[ReadStreamEvents] {
  protected val _streamId = EventStream(streamId)
  protected var _fromNumber: EventNumber = EventNumber.First
  protected var _direction = ReadDirection.Forward

  def fromNumber(x: Int) = set {
    _fromNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def fromFirst = set {
    _fromNumber = EventNumber.First
  }

  def fromLast = set {
    _fromNumber = EventNumber.Last
    _direction = ReadDirection.Backward
  }

  protected var _maxCount = 500

  def maxCount(x: Int) = set {
    _maxCount = x
  }

  def forward = set {
    _direction = ReadDirection.Forward
  }

  def backward = {
    _direction = ReadDirection.Backward
  }

  def build = ReadStreamEvents(
    streamId = _streamId,
    fromNumber = _fromNumber,
    maxCount = _maxCount,
    direction = _direction,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster)
}
