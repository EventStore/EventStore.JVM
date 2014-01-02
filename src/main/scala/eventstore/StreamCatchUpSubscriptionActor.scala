package eventstore

import akka.actor.{ Props, ActorRef }
import akka.actor.Status.Failure
import ReadDirection.Forward
import scala.collection.immutable.Queue

object StreamCatchUpSubscriptionActor {
  def props(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber.Exact] = None,
    resolveLinkTos: Boolean = false,
    readBatchSize: Int = 500): Props = Props(classOf[StreamCatchUpSubscriptionActor], connection, client,
    streamId, fromNumberExclusive, resolveLinkTos, readBatchSize)
}

class StreamCatchUpSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor {

  def receive = read(
    lastNumber = fromNumberExclusive,
    nextNumber = fromNumberExclusive getOrElse EventNumber.First)

  def read(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    readEventsFrom(nextNumber)

    {
      case ReadStreamEventsCompleted(events, next, _, endOfStream, _, Forward) => context become {
        val last = process(lastNumber, events)
        if (endOfStream) subscribe(last, next) else read(last, next)
      }

      case Failure(e) => e match {
        case e: EsException if e.reason == EsError.StreamNotFound =>
          context become subscribe(lastNumber, nextNumber)

        case _ => throw e
      }
    }
  }

  def subscribe(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    subscribeToStream(lastNumber)
    subscriptionFailed orElse {
      case SubscribeToStreamCompleted(_, subscriptionNumber) => context become {
        subscribed = true
        debug("subscribed at eventNumber: {}", subscriptionNumber)
        subscriptionNumber match {
          case Some(x) if !lastNumber.exists(_ >= x) =>
            debug("catch up events from lastNumber: {} to subscription eventNumber: {}", lastNumber, subscriptionNumber)
            catchUp(lastNumber = lastNumber, nextNumber = nextNumber, subscriptionNumber = x)

          case _ => liveProcessing(lastNumber)
        }
      }
    }
  }

  def catchUp(
    lastNumber: Option[EventNumber.Exact],
    nextNumber: EventNumber,
    subscriptionNumber: EventNumber.Exact,
    stash: Queue[Event] = Queue()): Receive = {

    readEventsFrom(nextNumber)

    def catchingUp(stash: Queue[Event]): Receive = {
      case ReadStreamEventsCompleted(events, next, _, endOfStream, _, Forward) => context become (
        if (endOfStream) liveProcessing(process(lastNumber, events), stash)
        else {
          def loop(events: List[Event], lastNumber: Option[EventNumber.Exact]): Receive = events match {
            case Nil => catchUp(lastNumber = lastNumber, nextNumber = next, subscriptionNumber = subscriptionNumber, stash)
            case event :: tail =>
              val number = event.record.number
              if (lastNumber.exists(_ >= number)) loop(tail, lastNumber)
              else if (number > subscriptionNumber) liveProcessing(lastNumber, stash)
              else {
                forward(event)
                loop(tail, Some(number))
              }
          }
          loop(events, lastNumber)
        })

      case Failure(e) => throw e

      case StreamEventAppeared(x) if x.event.record.number > subscriptionNumber =>
        debug("catching up: adding appeared event to stash({}): {}", stash.size, x)
        context become catchingUp(stash enqueue x.event)
    }

    catchingUp(stash)
  }

  def liveProcessing(lastNumber: Option[EventNumber.Exact], stash: Queue[Event] = Queue()): Receive = {
    debug("live processing started, lastEventNumber: {}", lastNumber)
    client ! Cs.LiveProcessingStarted

    def liveProcessing(lastNumber: Option[EventNumber.Exact]): Receive = {
      case StreamEventAppeared(x) => context become liveProcessing(process(lastNumber, x.event))
    }
    liveProcessing(process(lastNumber, stash))
  }

  def process(lastNumber: Option[EventNumber.Exact], events: Seq[Event]): Option[EventNumber.Exact] =
    events.foldLeft(lastNumber)((lastNumber, event) => process(lastNumber, event))

  def process(lastNumber: Option[EventNumber.Exact], event: Event): Option[EventNumber.Exact] = {
    val number = event.record.number
    lastNumber match {
      case Some(last) if last >= number =>
        log.warning("{}: event.number <= lastNumber: {} <= {}, dropping {}", streamId, number, last, event)
        lastNumber
      case _ =>
        forward(event)
        Some(number)
    }
  }

  def readEventsFrom(number: EventNumber) {
    debug("reading events from {}", number)
    connection ! ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def forward(event: Event) {
    debug("forwarding {}", event)
    client ! Cs.StreamEvent(event)
  }
}