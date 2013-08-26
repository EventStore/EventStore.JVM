package eventstore

import util.BetterToString

/**
 * @author Yaroslav Klymko
 */
sealed trait Event extends Ordered[Event] {
  def streamId: EventStream.Id
  def number: EventNumber.Exact
  def data: EventData
  def record: EventRecord

  def compare(that: Event) = this.number.value compare that.number.value

  def link(eventId: Uuid, metadata: ByteString = ByteString()): EventData = EventData(
    eventId = eventId,
    eventType = "$>",
    data = ByteString(s"${number.value}@${streamId.value}"),
    metadata = metadata)
}

object Event {
  object StreamDeleted {
    def unapply(x: Event): Option[(EventStream.Id, EventNumber.Exact, Uuid)] =
      EventData.StreamDeleted.unapply(x.data).map(uuid => (x.streamId, x.number, uuid))
  }
}

case class EventRecord(streamId: EventStream.Id, number: EventNumber.Exact, data: EventData) extends Event {
  def record = this
}

case class ResolvedEvent(linkedEvent: EventRecord, linkEvent: EventRecord) extends Event {
  def streamId = linkedEvent.streamId
  def number = linkedEvent.number
  def data = linkedEvent.data
  def record = linkEvent
}

case class EventData(
  eventId: Uuid,
  eventType: String,
  //                 dataContentType: Int, // TODO
  data: ByteString,
  //                 metadataContentType: Int, // TODO
  metadata: ByteString) extends BetterToString

object EventData {
  def apply(eventId: Uuid, eventType: String): EventData = EventData(eventId, eventType, ByteString.empty, ByteString.empty)

  object StreamDeleted {
    def unapply(x: EventData): Option[Uuid] = PartialFunction.condOpt(x) {
      case EventData(eventId, EvenType.streamDeleted, ByteString.empty, ByteString.empty) => eventId
    }
  }
}

case class IndexedEvent(event: Event, position: Position.Exact) extends Ordered[IndexedEvent] {
  def compare(that: IndexedEvent) = this.position compare that.position
}

object EvenType {
  val streamDeleted = "$streamDeleted" // TODO
  val streamCreated = "$streamCreated" // TODO
}