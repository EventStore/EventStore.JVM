package eventstore

import akka.actor.ActorRef
import scala.collection.immutable.Queue
import ReadDirection.Forward
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class StreamCatchUpSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor {

  def this(connection: ActorRef, client: ActorRef, streamId: EventStream.Id) =
    this(connection, client, streamId, None, false, 500)

  def receive = read(
    lastNumber = fromNumberExclusive,
    nextNumber = fromNumberExclusive getOrElse EventNumber.First)

  def read(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    readEventsFrom(nextNumber)

    {
      case ReadStreamEventsSucceed(events, next, _, endOfStream, _, Forward) => context become {
        val last = process(lastNumber, events)
        if (endOfStream) subscribe(last, next) else read(last, next)
      }

      case ReadStreamEventsFailed(reason, message, Forward) => context become (reason match {
        case ReadStreamEventsFailed.NoStream => subscribe(lastNumber, nextNumber)
        case _ => EventStore.error(reason, message)
      })
    }
  }

  def subscribe(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    subscribeToStream(s"lastEventNumber: $lastNumber")
    subscriptionFailed(s"lastEventNumber: $lastNumber") orElse {
      case SubscribeToStreamCompleted(_, subscriptionNumber) => context become {
        subscribed = true
        debug(s"subscribed at eventNumber: $subscriptionNumber")
        subscriptionNumber match {
          case Some(x) if !lastNumber.exists(_ >= x) =>
            debug(s"catch up events from lastNumber: $lastNumber to subscription eventNumber: $subscriptionNumber")
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
      case ReadStreamEventsSucceed(events, next, _, endOfStream, _, Forward) => context become (
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
          loop(events.toList, lastNumber)
        })

      case ReadStreamEventsFailed(reason, message, Forward) => EventStore.error(reason, message)

      case StreamEventAppeared(x) if x.event.record.number > subscriptionNumber =>
        debug(s"catching up: adding appeared event to stash(${stash.size}): $x")
        context become catchingUp(stash enqueue x.event)
    }

    catchingUp(stash)
  }

  def liveProcessing(lastNumber: Option[EventNumber.Exact], stash: Queue[Event] = Queue()): Receive = {
    debug(s"live processing started, lastEventNumber: $lastNumber")
    client ! LiveProcessingStarted

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
        log.warning(s"$streamId: event.number <= lastNumber: $number <= $last, dropping $event")
        lastNumber
      case _ =>
        forward(event)
        Some(number)
    }
  }

  def readEventsFrom(number: EventNumber) {
    debug(s"reading events from $number")
    connection ! ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def forward(event: Event) {
    debug(s"forwarding $event")
    client ! event // TODO put in to envelope
  }

  def forward(event: IndexedEvent) {
    forward(event.event)
  }
}