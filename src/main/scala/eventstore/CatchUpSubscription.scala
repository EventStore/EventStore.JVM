package eventstore

/**
 * @author Yaroslav Klymko
 */
object CatchUpSubscription {
//  sealed trait Event
//  sealed trait Command
  case object Stop /*extends Command*/
  case object LiveProcessingStarted /*extends Event */// TODO
  case class EventAppeared(event: ResolvedEvent) /*extends Event*/
}