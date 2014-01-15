package eventstore

import ReadDirection.Forward
import akka.actor.Status.Failure
import akka.actor.{ Props, ActorRef }
import scala.annotation.tailrec
import scala.collection.immutable.Queue

object StreamSubscriptionActor {
  def props(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber] = None,
    resolveLinkTos: Boolean = false,
    readBatchSize: Int = 100): Props = Props(classOf[StreamSubscriptionActor], connection, client,
    streamId, fromNumberExclusive, resolveLinkTos, readBatchSize)
}

class StreamSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor[Event] {

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  def receive = fromNumberExclusive match {
    case Some(EventNumber.Last)     => subscribingFromLast()
    case Some(x: EventNumber.Exact) => reading(Some(x), x)
    case None                       => reading(None, EventNumber.First)
  }

  def reading(last: Last, next: Next): Receive = {
    readEventsFrom(next)
    val receive: Receive = {
      case Failure(EsException(EsError.StreamNotFound, _)) => context become subscribing(last, next)
      case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) => context become {
        val l = process(last, events)
        if (endOfStream) subscribing(l, n) else reading(l, n)
      }
    }
    receive orElse rcvFailure orElse rcvReconnected(reading(last, next))
  }

  def subscribing(last: Last, next: Next): Receive = {
    subscribeToStream()
    rcvFailureOrUnsubscribe orElse rcvReconnected(last, next) orElse rcvSubscribeCompleted {
      case Some(x) if !last.exists(_ >= x) => catchingUp(last, next, x, Queue())
      case _                               => liveProcessing(last, Queue())
    }
  }

  def subscribingFromLast(): Receive = {
    subscribeToStream()
    rcvFailureOrUnsubscribe orElse rcvReconnected(subscribingFromLast()) orElse rcvSubscribeCompleted {
      liveProcessing(_, Queue())
    }
  }

  def catchingUp(last: Last, next: Next, subscriptionNumber: Next, stash: Queue[Event]): Receive = {
    readEventsFrom(next)

    def catchUp(stash: Queue[Event]): Receive = rcvFailureOrUnsubscribe orElse rcvReconnected(last, next) orElse {
      case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) => context become (
        if (endOfStream) liveProcessing(process(last, events), stash)
        else {
          @tailrec def loop(events: List[Event], last: Last): Receive = events match {
            case Nil => catchingUp(last, n, subscriptionNumber, stash)
            case event :: tail =>
              val number = event.record.number
              if (last.exists(_ >= number)) loop(tail, last)
              else if (number > subscriptionNumber) liveProcessing(last, stash)
              else {
                forward(event)
                loop(tail, Some(number))
              }
          }
          loop(events, last)
        })

      case StreamEventAppeared(x) => context become catchUp(stash enqueue x.event)
    }

    catchUp(stash)
  }

  def liveProcessing(last: Last, stash: Queue[Event]): Receive = {
    def liveProcessing(last: Last): Receive =
      rcvFailureOrUnsubscribe orElse rcvReconnected(last, last getOrElse EventNumber.First) orElse {
        case StreamEventAppeared(x) => context become liveProcessing(process(last, x.event))
      }

    client ! Subscription.LiveProcessingStarted
    liveProcessing(process(last, stash))
  }

  def process(last: Last, event: Event): Last = {
    val number = event.record.number
    if (last.exists(_ >= number)) last
    else {
      forward(event)
      Some(number)
    }
  }

  def readEventsFrom(number: Next) {
    connection ! ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def rcvSubscribeCompleted(receive: Last => Receive): Receive = {
    case SubscribeToStreamCompleted(_, subscriptionNumber) =>
      subscribed = true
      context become receive(subscriptionNumber)
  }
}