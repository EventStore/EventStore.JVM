package eventstore

/**
 * @author Yaroslav Klymko
 *
 * Cs - abbreviation for Catch-Up Subscription
 */
object Cs {
  sealed trait Event

  case object LiveProcessingStarted extends Event

  case class StreamEvent(event: eventstore.Event) extends Event

  case class AllStreamsEvent(event: IndexedEvent) extends Event
}