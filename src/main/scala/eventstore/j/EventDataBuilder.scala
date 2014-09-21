package eventstore
package j

class EventDataBuilder(val eventType: String) extends Builder[EventData] with ChainSet[EventDataBuilder] {
  protected var _eventId: Uuid = randomUuid
  protected var _data: Content = Content.Empty
  protected var _metadata: Content = Content.Empty

  def eventId(x: Uuid): EventDataBuilder = set {
    _eventId = x
  }

  def data(x: Content): EventDataBuilder = set {
    _data = x
  }

  def data(x: ByteString): EventDataBuilder = data(Content(x))

  def data(x: String): EventDataBuilder = data(Content(x))

  def data(xs: Array[Byte]): EventDataBuilder = data(Content(xs))

  def jsonData(x: String): EventDataBuilder = data(Content.Json(x))

  def metadata(x: Content): EventDataBuilder = set {
    _metadata = x
  }

  def metadata(x: ByteString): EventDataBuilder = metadata(Content(x))

  def metadata(x: String): EventDataBuilder = metadata(Content(x))

  def metadata(xs: Array[Byte]): EventDataBuilder = metadata(Content(xs))

  def jsonMetadata(x: String): EventDataBuilder = metadata(Content.Json(x))

  def build: EventData = EventData(
    eventType = eventType,
    eventId = _eventId,
    data = _data,
    metadata = _metadata)
}