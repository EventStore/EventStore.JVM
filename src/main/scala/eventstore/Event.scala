package eventstore

import eventstore.util.ByteStringToString
import org.joda.time.DateTime
import scala.PartialFunction.{ condOpt, cond }

sealed trait Event extends Ordered[Event] {
  def streamId: EventStream.Id
  def number: EventNumber.Exact
  def data: EventData
  def record: EventRecord
  def created: Option[DateTime]

  def compare(that: Event) = this.number.value compare that.number.value

  def link(eventId: Uuid = randomUuid, metadata: Content = Content.Empty): EventData = EventData(
    eventType = SystemEventType.linkTo,
    eventId = eventId,
    data = Content(s"${number.value}@${streamId.value}"),
    metadata = metadata)
}

object Event {
  object StreamDeleted {
    def unapply(x: Event): Boolean = cond(x.record) {
      case EventRecord(_, EventNumber.Exact(Int.MaxValue), EventData.StreamDeleted(), _) => true
    }
  }

  object StreamMetadata {
    def unapply(x: Event): Option[Content] = condOpt(x.record) {
      case EventRecord(_: EventStream.Metadata, _, EventData.StreamMetadata(metadata), _) => metadata
    }
  }
}

case class EventRecord(
    streamId: EventStream.Id,
    number: EventNumber.Exact,
    data: EventData,
    created: Option[DateTime] = None) extends Event {
  def record = this
}

case class ResolvedEvent(linkedEvent: EventRecord, linkEvent: EventRecord) extends Event {
  def streamId = linkedEvent.streamId
  def number = linkedEvent.number
  def data = linkedEvent.data
  def record = linkEvent
  def created = linkedEvent.created
}

case class Content(value: ByteString = ByteString.empty, contentType: ContentType = ContentType.Binary) {
  override lazy val toString = {
    val data = contentType match {
      case ContentType.Json if value.nonEmpty => value.utf8String
      case _                                  => ByteStringToString(value)
    }
    s"Content($data,$contentType)"
  }
}

object Content {
  val Empty: Content = Content()

  def apply(content: String): Content = Content(ByteString(content))

  def apply(content: Array[Byte]): Content = Content(ByteString(content))

  object Json {
    def apply(content: String): Content = Content(ByteString(content), ContentType.Json)

    def unapply(content: Content): Option[String] = condOpt(content) {
      case Content(x, ContentType.Json) => x.utf8String
    }
  }
}

case class EventData(
    eventType: String,
    eventId: Uuid = randomUuid,
    data: Content = Content.Empty,
    metadata: Content = Content.Empty) {
  require(eventType != null, "eventType is null")
  require(eventType.nonEmpty, "eventType is empty")
}

object EventData {

  object Json {
    def apply(eventType: String, eventId: Uuid = randomUuid, data: String = "", metadata: String = ""): EventData =
      EventData(eventType, eventId, data = Content.Json(data), metadata = Content.Json(metadata))
  }

  object StreamDeleted {
    import Content.Empty

    def apply(eventId: Uuid): EventData = EventData(SystemEventType.streamDeleted, eventId, Empty, Empty)

    def unapply(x: EventData): Boolean = cond(x) {
      case EventData(SystemEventType.streamDeleted, _, Empty, Empty) => true
    }
  }

  object StreamMetadata {
    def apply(data: Content, eventId: Uuid = randomUuid): EventData = {
      EventData(SystemEventType.metadata, eventId, data)
    }

    def unapply(x: EventData): Option[Content] = condOpt(x) {
      case EventData(SystemEventType.metadata, _, data, _) => data
    }
  }
}

case class IndexedEvent(event: Event, position: Position.Exact) extends Ordered[IndexedEvent] {
  def compare(that: IndexedEvent) = this.position compare that.position
}