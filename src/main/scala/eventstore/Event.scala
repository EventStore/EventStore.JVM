package eventstore

import util.BetterToString
import scala.PartialFunction.condOpt

sealed trait Event extends Ordered[Event] {
  def streamId: EventStream.Id
  def number: EventNumber.Exact
  def data: EventData
  def record: EventRecord
  //  def created: Option[Long] // TODO verify when it is optional

  def compare(that: Event) = this.number.value compare that.number.value

  def link(eventId: Uuid = randomUuid, metadata: Content = Content.empty): EventData = EventData(
    eventType = SystemEventType.linkTo,
    eventId = eventId,
    data = Content(s"${number.value}@${streamId.value}"),
    metadata = metadata)
}

object Event {
  object StreamDeleted {
    def unapply(x: Event): Option[(EventStream.Id, Uuid)] = condOpt(x.record) {
      case EventRecord(streamId, EventNumber.Exact(Int.MaxValue), EventData.StreamDeleted(uuid)) => (streamId, uuid)
    }
  }
}

// TODO find out how to convert .Net DateTime to java.util.Date
// TODO is it a property of EventRecord or Event ?
case class EventRecord(streamId: EventStream.Id, number: EventNumber.Exact, data: EventData /*, created: Option[Long] = None TODO*/ ) extends Event {
  def record = this
}

case class ResolvedEvent(linkedEvent: EventRecord, linkEvent: EventRecord) extends Event {
  def streamId = linkedEvent.streamId
  def number = linkedEvent.number
  def data = linkedEvent.data
  def record = linkEvent
  //  def created = linkEvent.created
}

case class Content(value: ByteString = ByteString.empty, contentType: ContentType = ContentType.Binary)
  extends BetterToString

object Content {
  val empty: Content = Content()

  def apply(content: String): Content = Content(ByteString(content))

  def apply(content: Array[Byte]): Content = Content(ByteString(content))

  object Json {
    def apply(content: String): Content = Content(ByteString(content), ContentType.Json)

    def unapply(content: Content): Option[String] = PartialFunction.condOpt(content) {
      case Content(x, ContentType.Json) => x.utf8String
    }
  }
}

case class EventData(
    eventType: String,
    eventId: Uuid = randomUuid,
    data: Content = Content.empty,
    metadata: Content = Content.empty) {
  require(eventType != null, "eventType is null")
  require(eventType.nonEmpty, "eventType is empty")
}

object EventData {

  object Json {
    def apply(eventType: String, eventId: Uuid = randomUuid, data: String = "", metadata: String = ""): EventData =
      EventData(eventType, eventId, data = Content.Json(data), metadata = Content.Json(metadata))
  }

  object StreamDeleted {
    import Content.empty

    def apply(eventId: Uuid): EventData = EventData(SystemEventType.streamDeleted, eventId, empty, empty)

    def unapply(x: EventData): Option[Uuid] = condOpt(x) {
      case EventData(SystemEventType.streamDeleted, eventId, `empty`, `empty`) => eventId
    }
  }

  object StreamMetadata {
    def apply(data: Content = Content.empty, eventId: Uuid = randomUuid): EventData =
      EventData(SystemEventType.metadata, data = data)

    def unapply(x: EventData): Option[Content] = condOpt(x) {
      case EventData(SystemEventType.metadata, _, data, _) => data
    }
  }
}

case class IndexedEvent(event: Event, position: Position.Exact) extends Ordered[IndexedEvent] {
  def compare(that: IndexedEvent) = this.position compare that.position
}