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
                               readBatchSize: Int = 500) extends Actor with ActorLogging {

  val streamId = AllStreams

  // val maxPushQueueSize = 10000 // TODO implement

  def receive = read(
    lastPosition = fromPositionExclusive,
    nextPosition = fromPositionExclusive getOrElse Position.start)

  def read(lastPosition: Option[Position], nextPosition: Position): Receive = {
    readEventsFrom(nextPosition)
    read(lastPosition)
  }

  def read(lastPosition: Option[Position]): Receive = {
    case StopSubscription => unsubscribed(SubscriptionDropped.Unsubscribed)

    case ReadAllEventsCompleted(_, events, nextPosition, Forward) => context become (
      if (events.nonEmpty) read(lastPosition = process(lastPosition, events), nextPosition = nextPosition)
      else subscribe(lastPosition = lastPosition, nextPosition = nextPosition))
  }

  def catchUp(lastPosition: Option[Position],
              nextPosition: Position,
              subscriptionLastCommit: Long,
              stash: Queue[ResolvedEvent]): Receive = {

    readEventsFrom(nextPosition)

    def catchingUp(stash: Queue[ResolvedEvent]): Receive = {
      case ReadAllEventsCompleted(_, events, np, Forward) => context become (
        if (events.isEmpty) liveProcessing(lastPosition, stash)
        else {
          def loop(events: List[ResolvedEvent], lastPosition: Option[Position]): Receive = events match {
            case Nil => catchUp(lastPosition, np, subscriptionLastCommit, stash)
            case event :: tail =>
              val position = event.position
              if (lastPosition.exists(_ >= position)) loop(tail, lastPosition)
              else if (position.commitPosition > subscriptionLastCommit) liveProcessing(lastPosition, stash)
              else {
                forward(event)
                loop(tail, Some(position))
              }
          }
          loop(events.toList, lastPosition)
        })

      case StreamEventAppeared(x) if x.position.commitPosition > subscriptionLastCommit =>
        debug(s"catching up: adding appeared event to stash(${stash.size}): $x")
        context become catchingUp(stash enqueue x)

      case StopSubscription => unsubscribe
    }

    catchingUp(stash)
  }

  def subscribe(lastPosition: Option[Position], nextPosition: Position): Receive = {
    debug(s"subscribing: lastPosition: $lastPosition")
    connection ! SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def subscriptionFailed: Receive = {
      case SubscriptionDropped(x) =>
        log.warning(s"$streamId: subscription failed: $x, lastPosition: $lastPosition")
        unsubscribed(x)
    }

    subscriptionFailed orElse {
      case SubscribeToAllCompleted(lastCommit) =>
        debug(s"subscribed at lastCommit: $lastCommit")
        context become (
          if (lastPosition.exists(_.commitPosition >= lastCommit)) liveProcessing(lastPosition, Queue())
          else {
            debug(s"catch up events from lastPosition: $lastPosition to subscriptionLastCommit: $lastCommit")
            catchUp(lastPosition, nextPosition, lastCommit, Queue())
          })

      case StopSubscription => context become subscriptionFailed.orElse {
        case _: SubscribeToAllCompleted => unsubscribe
      }
    }
  }

  def liveProcessing(lastPosition: Option[Position], stash: Queue[ResolvedEvent]): Receive = {
    debug(s"live processing started, lastPosition: $lastPosition")
    client ! LiveProcessingStarted

    def liveProcessing(lastPosition: Option[Position]): Receive = {
      case StreamEventAppeared(event) => context become liveProcessing(process(lastPosition, event))
      case StopSubscription => unsubscribe
    }
    liveProcessing(process(lastPosition, stash))
  }

  def unsubscribe {
    debug("unsubscribing")
    connection ! UnsubscribeFromStream
    context become {
      case SubscriptionDropped(SubscriptionDropped.Unsubscribed) => unsubscribed(SubscriptionDropped.Unsubscribed)
    }
  }

  def unsubscribed(reason: SubscriptionDropped.Value) {
    debug("unsubscribed")
    client ! SubscriptionDropped(reason)
    context become {
      case msg => debug(s"received while unsubscribed $msg")
    }
  }

  def process(lastPosition: Option[Position], events: Seq[ResolvedEvent]): Option[Position] =
    events.foldLeft(lastPosition)((lastPosition, event) => process(lastPosition, event))

  def process(lastPosition: Option[Position], event: ResolvedEvent): Option[Position] = {
    val position = event.position
    lastPosition match {
      case Some(lp) if lp >= position =>
        log.warning(s"$streamId: dropping $event: event.position <= lastPosition: $position <= $lp, dropping $event")
        lastPosition
      case _ =>
        forward(event)
        Some(event.position)
    }
  }

  def readEventsFrom(position: Position) {
    debug(s"reading events from $position")
    connection ! ReadAllEvents(position, readBatchSize, resolveLinkTos = resolveLinkTos, Forward)
  }

  def forward(event: ResolvedEvent) {
    debug(s"forwarding $event")
    client ! event
  }

  def debug(msg: => String) {
    log.debug(s"$streamId: $msg")
  }
}

case object StopSubscription
case object LiveProcessingStarted