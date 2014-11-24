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
    resolveLinkTos: Boolean = Settings.Default.resolveLinkTos,
    credentials: Option[UserCredentials] = None,
    readBatchSize: Int = Settings.Default.readBatchSize): Props = Props(classOf[StreamSubscriptionActor], connection, client, streamId,
    fromNumberExclusive, resolveLinkTos, credentials, readBatchSize)

  /**
   * Java API
   */
  def getProps(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos: Boolean,
    credentials: Option[UserCredentials],
    readBatchSize: Int): Props =
    props(connection, client, streamId, fromNumberExclusive, resolveLinkTos, credentials, readBatchSize)

  /**
   * Java API
   */
  def getProps(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber]): Props = props(connection, client, streamId, fromNumberExclusive)
}

class StreamSubscriptionActor private (
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    val resolveLinkTos: Boolean,
    val credentials: Option[UserCredentials],
    val readBatchSize: Int) extends AbstractSubscriptionActor[Event] {

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  def receive = fromNumberExclusive match {
    case Some(EventNumber.Last)     => subscribingFromLast()
    case Some(x: EventNumber.Exact) => reading(Some(x), x, ready = true)
    case None                       => reading(None, EventNumber.First, ready = true)
  }

  def reading(last: Last, next: Next, ready: Boolean): Receive = {
    def rcv(ready: Boolean): Receive = {
      def read(events: List[Event], n: Next, endOfStream: Boolean) = {
        val l = process(last, events)
        if (endOfStream) subscribing(l, n)
        else whenReady(reading(l, n, ready = false), ready)
      }

      rcvReadCompleted(read) orElse
        rcvStreamNotFound(subscribing(last, next)) orElse
        rcvReconnected(reading(last, next, ready = true)) orElse
        rcvFailure
    }

    readEventsFrom(next)
    rcv(ready) orElse rcvReady(rcv(ready = true))
  }

  def subscribing(last: Last, next: Next): Receive = {
    def subscribed(subscriptionNumber: Last) = {
      subscriptionNumber match {
        case Some(x) if !last.exists(_ >= x) => catchingUp(last, next, x, Queue())
        case _                               => liveProcessing(last, Queue())
      }
    }

    subscribeToStream()
    rcvReconnected(last, next) orElse
      rcvSubscribeCompleted(subscribed) orElse
      rcvFailureOrUnsubscribe
  }

  def subscribingFromLast(): Receive = {
    def subscribed(number: Last) = {
      liveProcessing(number, Queue())
    }

    subscribeToStream()
    rcvReconnected(subscribingFromLast()) orElse
      rcvSubscribeCompleted(subscribed) orElse
      rcvFailureOrUnsubscribe
  }

  def catchingUp(last: Last, next: Next, subscriptionNumber: Next, stash: Queue[Event]): Receive = {
    def catchUp(subscriptionNumber: Next, stash: Queue[Event]): Receive = {
      def read(events: List[Event], n: Next, endOfStream: Boolean) = {
        if (endOfStream) liveProcessing(process(last, events), stash)
        else {
          @tailrec def loop(events: List[Event], last: Last): Receive = events match {
            case Nil => catchingUp(last, n, subscriptionNumber, stash)
            case event :: tail =>
              val number = event.record.number
              if (last.exists(_ >= number)) loop(tail, last)
              else if (number > subscriptionNumber) liveProcessing(last, stash)
              else {
                toClient(event)
                loop(tail, Some(number))
              }
          }
          loop(events, last)
        }
      }

      def eventAppeared(event: IndexedEvent) = {
        catchUp(subscriptionNumber, stash enqueue event.event)
      }

      def subscribed(number: Last) = {
        catchUp(subscriptionNumber, Queue())
      }

      rcvReconnected(last, next) orElse
        rcvEventAppeared(eventAppeared) orElse
        rcvSubscribeCompleted(subscribed) orElse
        rcvReadCompleted(read) orElse
        rcvFailureOrUnsubscribe
    }

    readEventsFrom(next)
    catchUp(subscriptionNumber, stash)
  }

  def liveProcessing(last: Last, stash: Queue[Event]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive = {
      def eventAppeared(event: IndexedEvent) = {
        val l = process(last, event.event)
        if (n < readBatchSize) liveProcessing(l, n + 1, ready)
        else {
          checkReadiness()
          if (ready) liveProcessing(l, 0, ready = false)
          else {
            unsubscribe()
            rcvReady(reading(l, l getOrElse EventNumber.First, ready = false)) orElse
              ignoreUnsubscribed orElse
              rcvFailure
          }
        }
      }

      def subscribed(number: Last) = {
        (number, last) match {
          case (Some(number), Some(last)) if number > last => catchingUp(Some(last), last, number, Queue())
          case _ => liveProcessing(last, n, ready)
        }
      }

      rcvReconnected(last, last getOrElse EventNumber.First) orElse
        rcvEventAppeared(eventAppeared) orElse
        rcvReady(liveProcessing(last, n, ready = true)) orElse
        rcvSubscribeCompleted(subscribed) orElse
        rcvFailureOrUnsubscribe
    }

    client ! LiveProcessingStarted
    liveProcessing(process(last, stash), 0, ready = true)
  }

  def process(last: Last, event: Event): Last = {
    val number = event.record.number
    if (last.exists(_ >= number)) last
    else {
      toClient(event)
      Some(number)
    }
  }

  def readEventsFrom(number: Next) = {
    toConnection(ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos))
  }

  def rcvSubscribeCompleted(receive: Last => Receive): Receive = {
    case SubscribeToStreamCompleted(_, subscriptionNumber) => context become receive(subscriptionNumber)
  }

  def rcvReadCompleted(receive: (List[Event], Next, Boolean) => Receive): Receive = {
    case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) =>
      context become receive(events, n, endOfStream)
  }

  def rcvStreamNotFound(receive: => Receive): Receive = {
    case Failure(_: StreamNotFoundException) => context become receive
  }
}