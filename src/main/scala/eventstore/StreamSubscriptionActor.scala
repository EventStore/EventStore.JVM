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
    fromNumberExclusive: Option[EventNumber.Exact] = None,
    resolveLinkTos: Boolean = false,
    readBatchSize: Int = 500): Props = Props(classOf[StreamSubscriptionActor], connection, client,
    streamId, fromNumberExclusive, resolveLinkTos, readBatchSize)
}

class StreamSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor[Event] {

  def receive = reading(fromNumberExclusive, fromNumberExclusive getOrElse EventNumber.First)

  def reading(last: Option[EventNumber.Exact], next: EventNumber): Receive = {
    readEventsFrom(next)

    val receive: Receive = {
      case ReadStreamEventsCompleted(events, n, _, endOfStream, _, Forward) => context become {
        val l = process(last, events)
        if (endOfStream) subscribing(l, n) else reading(l, n)
      }

      case Failure(EsException(EsError.StreamNotFound, _)) => context become subscribing(last, next)
    }
    receive orElse rcvFailure orElse rcvReconnected(reading(last, next))
  }

  def subscribing(last: Option[EventNumber.Exact], next: EventNumber): Receive = {
    subscribeToStream()
    rcvFailure orElse rcvReconnected(last, next) orElse rcvUnsubscribe orElse {
      case SubscribeToStreamCompleted(_, subscriptionNumber) => context become {
        subscribed = true
        subscriptionNumber match {
          case Some(x) if !last.exists(_ >= x) => catchingUp(last, next, x)
          case _                               => liveProcessing(last)
        }
      }
    }
  }

  def catchingUp(
    last: Option[EventNumber.Exact],
    next: EventNumber,
    subscriptionNumber: EventNumber.Exact,
    stash: Queue[Event] = Queue()): Receive = {

    readEventsFrom(next)

    def catchUp(stash: Queue[Event]): Receive = rcvFailure orElse rcvReconnected(last, next) orElse {
      case ReadStreamEventsCompleted(events, n, _, endOfStream, _, Forward) => context become (
        if (endOfStream) liveProcessing(process(last, events), stash)
        else {
          @tailrec def loop(events: List[Event], last: Option[EventNumber.Exact]): Receive = events match {
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

  def liveProcessing(last: Option[EventNumber.Exact], stash: Queue[Event] = Queue()): Receive = {
    def liveProcessing(last: Option[EventNumber.Exact]): Receive =
      rcvFailure orElse rcvReconnected(last, last getOrElse EventNumber.First) orElse {
        case StreamEventAppeared(x) => context become liveProcessing(process(last, x.event))
      }

    client ! Subscription.LiveProcessingStarted
    liveProcessing(process(last, stash))
  }

  def process(lastNumber: Option[EventNumber.Exact], events: Seq[Event]): Option[EventNumber.Exact] =
    events.foldLeft(lastNumber)(process)

  def process(last: Option[EventNumber.Exact], event: Event): Option[EventNumber.Exact] = {
    val number = event.record.number
    if (last.exists(_ >= number)) last
    else {
      forward(event)
      Some(number)
    }
  }

  def readEventsFrom(number: EventNumber) {
    connection ! ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def rcvReconnected(last: Option[EventNumber.Exact], next: EventNumber): Receive =
    rcvReconnected(subscribing(last, next))
}