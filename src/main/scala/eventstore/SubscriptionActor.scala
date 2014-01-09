package eventstore

import ReadDirection.Forward
import akka.actor.{ Props, ActorRef }
import scala.collection.immutable.Queue
import scala.annotation.tailrec

object SubscriptionActor {
  def props(
    connection: ActorRef,
    client: ActorRef,
    fromPositionExclusive: Option[Position.Exact] = None,
    resolveLinkTos: Boolean = false,
    readBatchSize: Int = 500): Props =
    Props(classOf[SubscriptionActor], connection, client, fromPositionExclusive, resolveLinkTos, readBatchSize)
}

class SubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    fromPositionExclusive: Option[Position.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor[IndexedEvent] {

  val streamId = EventStream.All

  def receive = reading(fromPositionExclusive, fromPositionExclusive getOrElse Position.First)

  def reading(last: Option[Position.Exact], next: Position): Receive = {
    readEventsFrom(next)
    rcvFailure orElse rcvReconnected(reading(last, next)) orElse rcvReadCompleted {
      (events, next) =>
        if (events.nonEmpty) reading(process(last, events), next)
        else subscribing(last, next)
    }
  }

  def subscribing(last: Option[Position.Exact], next: Position): Receive = {
    subscribeToStream()
    rcvFailure orElse rcvReconnected(last, next) orElse rcvUnsubscribe orElse {
      case SubscribeToAllCompleted(lastCommit) =>
        subscribed = true
        context become {
          if (last.exists(_.commitPosition >= lastCommit)) liveProcessing(last, Queue())
          else catchingUp(last, next, lastCommit, Queue())
        }
    }
  }

  def catchingUp(
    last: Option[Position.Exact],
    next: Position,
    subscriptionCommit: Long,
    stash: Queue[IndexedEvent]): Receive = {
    def catchUp(stash: Queue[IndexedEvent]): Receive = rcvReadCompleted {
      (events, next) =>
        if (events.isEmpty) liveProcessing(last, stash)
        else {
          @tailrec def loop(events: List[IndexedEvent], last: Option[Position.Exact]): Receive = events match {
            case Nil => catchingUp(last, next, subscriptionCommit, stash)
            case event :: tail =>
              val position = event.position
              if (last.exists(_ >= position)) loop(tail, last)
              else if (position.commitPosition > subscriptionCommit) liveProcessing(last, stash)
              else {
                forward(event)
                loop(tail, Some(position))
              }
          }
          loop(events, last)
        }
    } orElse rcvFailure orElse rcvReconnected(last, next) orElse {
      case StreamEventAppeared(x) => context become catchUp(stash enqueue x)
    }

    readEventsFrom(next)
    catchUp(stash)
  }

  def liveProcessing(last: Option[Position.Exact], stash: Queue[IndexedEvent]): Receive = {
    def liveProcessing(last: Option[Position.Exact]): Receive =
      rcvFailure orElse rcvReconnected(last, last getOrElse Position.First) orElse {
        case StreamEventAppeared(x) => context become liveProcessing(process(last, x))
      }

    client ! Subscription.LiveProcessingStarted
    liveProcessing(process(last, stash))
  }

  def process(last: Option[Position.Exact], events: Seq[IndexedEvent]): Option[Position.Exact] =
    events.foldLeft(last)(process)

  def process(lastPosition: Option[Position.Exact], event: IndexedEvent): Option[Position.Exact] = {
    val position = event.position
    if (lastPosition.exists(_ >= position)) lastPosition
    else {
      forward(event)
      Some(position)
    }
  }

  def readEventsFrom(position: Position) {
    connection ! ReadAllEvents(position, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def rcvReadCompleted(f: (List[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsCompleted(events, _, next, Forward) => context become f(events, next)
  }

  def rcvReconnected(last: Option[Position.Exact], next: Position): Receive =
    rcvReconnected(subscribing(last, next))
}