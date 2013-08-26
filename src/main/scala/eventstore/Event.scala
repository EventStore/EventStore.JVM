package eventstore

import util.BetterToString

/**
 * @author Yaroslav Klymko
 */
case class Event(eventId: Uuid,
                 eventType: String,
                 //                 dataContentType: Int, // TODO
                 data: ByteString,
                 //                 metadataContentType: Int, // TODO
                 metadata: ByteString) extends BetterToString

object Event {

  def apply(eventId: Uuid, eventType: String): Event = Event(eventId, eventType, ByteString.empty, ByteString.empty)

  object StreamDeleted {
    def unapply(x: Event): Option[Uuid] = PartialFunction.condOpt(x) {
      case Event(eventId, EvenType.streamDeleted, ByteString.empty, ByteString.empty) => eventId
    }
  }

}


object EvenType {
  val streamDeleted = "$streamDeleted" // TODO
  val streamCreated = "$streamCreated" // TODO
}


case class EventRecord(streamId: EventStream.Id, number: EventNumber.Exact, event: Event) extends Ordered[EventRecord] {
  def compare(that: EventRecord) = this.number.value compare that.number.value

  def link(eventId: Uuid, metadata: ByteString = ByteString()): Event = Event(
    eventId = eventId,
    eventType = "$>",
    data = ByteString(s"${number.value}@${streamId.value}"),
    metadata = metadata)
}

object EventRecord {
  object StreamDeleted {
    def unapply(x: EventRecord): Option[(EventStream.Id, EventNumber.Exact, Uuid)] = PartialFunction.condOpt(x) {
      case EventRecord(streamId, eventNumber, Event.StreamDeleted(uuid)) => (streamId, eventNumber, uuid)
    }
  }
}

// TODO has common with ResolvedIndexedEvent structure.
case class ResolvedEvent(eventRecord: EventRecord,
                         link: Option[EventRecord], // TODO change the way links are provided to user
                         position: Position.Exact) extends Ordered[ResolvedEvent] {
  def compare(that: ResolvedEvent) = this.position compare that.position
}

// TODO has common with ResolvedEvent structure.
// TODO change the way links are provided to user
case class ResolvedIndexedEvent(eventRecord: EventRecord, link: Option[EventRecord]) extends Ordered[ResolvedIndexedEvent] {
  def compare(that: ResolvedIndexedEvent) = this.eventRecord compare that.eventRecord
}