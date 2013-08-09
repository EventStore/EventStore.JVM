package eventstore

import akka.actor.{ActorLogging, Actor, ActorRef}
import scala.collection.immutable.Queue
import ReadDirection.Forward

/**
 * @author Yaroslav Klymko
 */
class CatchUpSubscriptionActor(connection: ActorRef,
                               client: ActorRef,
                               fromPositionExclusive: Option[Position],
                               resolveLinkTos: Boolean,
                               userCredentials: Option[UserCredentials],
                               readBatchSize: Int = 500) extends Actor with ActorLogging {

  val maxPushQueueSize = 10000

  def receive = read(
    lastPosition = fromPositionExclusive,
    nextPosition = fromPositionExclusive getOrElse Position.start)

  def read(lastPosition: Option[Position], nextPosition: Position): Receive = {
    readEventsFrom(nextPosition)
    read(lastPosition)
  }

  def read(lastPosition: Option[Position]): Receive = {
    case ReadAllEventsCompleted(_, events, nextPosition, Forward) => context become (
      if (events.nonEmpty) read(lastPosition = process(lastPosition, events), nextPosition = nextPosition)
      else {
        log.debug(s"subscribe from $lastPosition")
        subscribe(lastPosition = lastPosition, nextPosition = nextPosition)
      })

    case StopSubscription =>
      client ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      context become PartialFunction.empty
  }

  def catchUp(lastPosition: Option[Position],
              nextPosition: Position,
              subscriptionLastCommit: Long,
              stash: Queue[ResolvedEvent]): Receive = {

    readEventsFrom(nextPosition)

    def catchingUp(stash: Queue[ResolvedEvent]): Receive = {
      case ReadAllEventsCompleted(_, events, np, Forward) => context become (
        if (events.isEmpty) subscribed(lastPosition, stash)
        else {
          def loop(events: List[ResolvedEvent], lastPosition: Option[Position]): Receive = events match {
            case Nil => catchUp(lastPosition, np, subscriptionLastCommit, stash)
            case event :: tail =>
              val position = event.position
              if (lastPosition.exists(_ >= position)) loop(tail, lastPosition)
              else if (position.commitPosition > subscriptionLastCommit) subscribed(lastPosition, stash)
              else {
                forward(event)
                loop(tail, Some(position))
              }
          }
          loop(events.toList, lastPosition)
        })

      case StreamEventAppeared(x) =>
        if (x.position.commitPosition > subscriptionLastCommit) context become catchingUp(stash enqueue x)

      case StopSubscription =>
        connection ! UnsubscribeFromStream
        context become {
          case x@SubscriptionDropped(SubscriptionDropped.Unsubscribed) => client ! x
        }
    }

    catchingUp(stash)
  }

  def subscribe(lastPosition: Option[Position], nextPosition: Position): Receive = {
    connection ! SubscribeTo(AllStreams, resolveLinkTos = resolveLinkTos)

    {
      case SubscribeToAllCompleted(lastCommit) => context become (
        if (lastPosition.exists(_.commitPosition >= lastCommit)) subscribed(lastPosition, Queue())
        else {
          log.debug(s"catchUp from $lastPosition")
          catchUp(lastPosition, nextPosition, lastCommit, Queue())
        })

      case StopSubscription => context become {
        case x: SubscriptionDropped => client ! x
        case _: SubscribeToAllCompleted =>
          connection ! UnsubscribeFromStream
          context become {
            case x@SubscriptionDropped(SubscriptionDropped.Unsubscribed) => client ! x
          }
      }
      case x: SubscriptionDropped => client ! x
    }
  }

  def subscribed(lastPosition: Option[Position], stash: Queue[ResolvedEvent]) = {
    log.debug(s"subscribed from $lastPosition")
    client ! LiveProcessingStarted

    def subscribed(lastPosition: Option[Position]): Receive = {
      case StreamEventAppeared(event) =>
        context become subscribed(process(lastPosition, event))

      case StopSubscription =>
        connection ! UnsubscribeFromStream
        context become {
          case x@SubscriptionDropped(SubscriptionDropped.Unsubscribed) => client ! x
        }
    }
    subscribed(process(lastPosition, stash))
  }

  def process(lastPosition: Option[Position], events: Seq[ResolvedEvent]): Option[Position] =
    events.foldLeft(lastPosition)((lastPosition, event) => process(lastPosition, event))

  def process(lastPosition: Option[Position], event: ResolvedEvent): Option[Position] = {
    val position = event.position
    lastPosition match {
      case Some(lp) if lp >= position =>
        log.warning(s"dropping $event: event.position <= lastPosition: $position <= $lp, dropping $event")
        lastPosition
      case _ =>
        forward(event)
        Some(event.position)
    }
  }

  def readEventsFrom(position: Position) {
    log.debug(s"reading events from $position")
    connection ! ReadAllEvents(position, readBatchSize, resolveLinkTos = resolveLinkTos, Forward)
  }

  def forward(event: ResolvedEvent) {
    log.debug(s"forwarding $event")
    client ! event
  }
}

case object StopSubscription
case object LiveProcessingStarted