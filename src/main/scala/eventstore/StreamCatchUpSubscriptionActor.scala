package eventstore

import akka.actor.{ ActorLogging, Actor, ActorRef }
import scala.collection.immutable.Queue
import ReadDirection.Forward
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class StreamCatchUpSubscriptionActor(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber.Exact],
    resolveLinkTos: Boolean,
    readBatchSize: Int) extends Actor with ActorLogging {

  def this(connection: ActorRef, client: ActorRef, streamId: EventStream.Id) =
    this(connection, client, streamId, None, false, 500)

  // val maxPushQueueSize = 10000 // TODO implement

  def receive = read(
    lastNumber = fromNumberExclusive,
    nextNumber = fromNumberExclusive getOrElse EventNumber.First)

  def read(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    readEventsFrom(nextNumber)

    {
      case Stop => unsubscribed(SubscriptionDropped.Unsubscribed)

      case msg: ReadStreamEventsCompleted => context become (msg match {
        case ReadStreamEventsSucceed(events, next, _, endOfStream, _, Forward) =>
          val last = process(lastNumber, events) // TODO
          if (endOfStream) subscribe(last, next) else read(last, next)

        case ReadStreamEventsFailed(reason, _, _, Forward) =>
          reason match {
            case ReadStreamEventsFailed.NoStream => subscribe(lastNumber, nextNumber)
            case x =>
              println(x)
              ???
          }

        case _: ReadStreamEventsFailed => ??? // TODO

      })
    }
  }

  def subscribe(lastNumber: Option[EventNumber.Exact], nextNumber: EventNumber): Receive = {
    debug(s"subscribing: lastEventNumber: $lastNumber")
    connection ! SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def subscriptionFailed: Receive = {
      case SubscriptionDropped(x) =>
        log.warning(s"$streamId: subscription failed: $x, lastEventNumber: $lastNumber")
        unsubscribed(x)
    }

    subscriptionFailed orElse {
      case SubscribeToStreamCompleted(_, subscriptionNumber) =>
        debug(s"subscribed at eventNumber: $subscriptionNumber")
        /*TODO refactor*/ context become ((lastNumber, subscriptionNumber) match {
          case (None, None) => liveProcessing(lastNumber)
          case (None, Some(x)) =>
            debug(s"catch up events from lastNumber: $lastNumber to subscription eventNumber: $subscriptionNumber")
            catchUp(lastNumber = lastNumber, nextNumber = nextNumber, subscriptionNumber = x)
          case (Some(x), None) => liveProcessing(lastNumber)
          case (Some(x), Some(y)) =>
            if (x >= y) liveProcessing(lastNumber)
            else {
              debug(s"catch up events from lastNumber: $lastNumber to subscription eventNumber: $subscriptionNumber")
              catchUp(lastNumber = lastNumber, nextNumber = nextNumber, subscriptionNumber = y)
            }
        })

      case Stop => context become subscriptionFailed.orElse {
        case _: SubscribeToStreamCompleted => unsubscribe()
      }
    }
  }

  def catchUp(lastNumber: Option[EventNumber.Exact],
    nextNumber: EventNumber,
    //              subscriptionLastCommit: Long,
    subscriptionNumber: EventNumber.Exact,
    stash: Queue[Event] = Queue()): Receive = {

    readEventsFrom(nextNumber)

    def catchingUp(stash: Queue[Event]): Receive = {
      case ReadStreamEventsSucceed(events, next, _, endOfStream, _, Forward) => context become (
        if (endOfStream) liveProcessing(process(lastNumber, events), stash) // TODO
        else {
          def loop(events: List[Event], lastNumber: Option[EventNumber.Exact]): Receive = events match {
            case Nil => catchUp(lastNumber = lastNumber, nextNumber = next, subscriptionNumber = subscriptionNumber, stash)
            case event :: tail =>
              val number = getNumber(event)
              if (lastNumber.exists(_ >= number)) loop(tail, lastNumber)
              else if (number > subscriptionNumber) liveProcessing(lastNumber, stash)
              else {
                forward(event)
                loop(tail, Some(number))
              }
          }
          loop(events.toList, lastNumber)
        })

      case _: ReadStreamEventsFailed => ??? // TODO

      case StreamEventAppeared(x) if getNumber(x.event) > subscriptionNumber => // TODO
        debug(s"catching up: adding appeared event to stash(${stash.size}): $x")
        context become catchingUp(stash enqueue x.event)

      case Stop => unsubscribe()
    }

    catchingUp(stash)
  }

  def liveProcessing(lastNumber: Option[EventNumber.Exact], stash: Queue[Event] = Queue()): Receive = {
    debug(s"live processing started, lastEventNumber: $lastNumber")
    client ! LiveProcessingStarted

    def liveProcessing(lastNumber: Option[EventNumber.Exact]): Receive = {
      case StreamEventAppeared(x) => context become liveProcessing(process(lastNumber, x.event))
      case Stop => unsubscribe()
    }
    liveProcessing(process(lastNumber, stash))
  }

  def unsubscribe() {
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

  @deprecated
  def getNumber(event: Event): EventNumber.Exact = event match {
    case x: ResolvedEvent => x.linkEvent.number
    case x: EventRecord => x.number
  }

  def process(lastNumber: Option[EventNumber.Exact], events: Seq[Event]): Option[EventNumber.Exact] =
    events.foldLeft(lastNumber)((lastNumber, event) => process(lastNumber, event))

  def process(lastNumber: Option[EventNumber.Exact], event: Event): Option[EventNumber.Exact] = {
    val number = getNumber(event)
    lastNumber match {
      case Some(last) if last >= number =>
        log.warning(s"$streamId: dropping $event: event.number <= lastNumber: $number <= $last, dropping $event")
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

  def debug(msg: => String) {
    log.debug(s"$streamId: $msg")
  }
}