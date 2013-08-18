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
  val streamDeleted = "$streamDeleted"
  // TODO
  val streamCreated = "$streamCreated" // TODO
}

case class ResolvedEvent(eventRecord: EventRecord,
                         link: Option[EventRecord],
                         position: Position.Exact) extends Ordered[ResolvedEvent] {
  def compare(that: ResolvedEvent) = this.position compare that.position
}

case class ResolvedIndexedEvent(eventRecord: EventRecord, link: Option[EventRecord]) extends Ordered[ResolvedIndexedEvent] {
  def compare(that: ResolvedIndexedEvent) = this.eventRecord compare that.eventRecord
}

case class EventRecord(streamId: EventStream.Id, number: EventNumber.Exact, event: Event) extends Ordered[EventRecord] {
  def compare(that: EventRecord) = this.number.value compare that.number.value
}
