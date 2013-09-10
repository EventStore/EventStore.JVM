package eventstore
package j

/**
 * @author Yaroslav Klymko
 */
class EventDataBuilder(val eventType: String) extends Builder[EventData] {
  private var _eventId: Uuid = newUuid
  private var _data: ByteString = ByteString.empty
  private var _metadata: ByteString = ByteString.empty

  def eventId(x: Uuid) = set {
    _eventId = x
  }

  def data(x: ByteString) = set {
    _data = x
  }

  def data(x: String) = set {
    _data = ByteString(x)
  }

  def data(xs: Array[Byte]) = set {
    _data = ByteString(xs)
  }

  def metadata(x: ByteString) = set {
    _metadata = x
  }

  def metadata(x: String) = set {
    _metadata = ByteString(x)
  }

  def metadata(xs: Array[Byte]) = set {
    _metadata = ByteString(xs)
  }

  def build = EventData(
    eventId = _eventId,
    eventType = eventType,
    data = _data,
    metadata = _metadata)
}