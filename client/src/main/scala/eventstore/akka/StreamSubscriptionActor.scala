package eventstore
package akka

import ReadDirection.Forward
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ActorRef, Props}
import scala.annotation.tailrec
import scala.collection.immutable.Queue

object StreamSubscriptionActor {

  def props(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    credentials:         Option[UserCredentials],
    settings:            Settings
  ): Props = {

    Props(new StreamSubscriptionActor(
      connection,
      client,
      streamId,
      fromNumberExclusive,
      credentials,
      settings
    ))
  }

  /**
   * Java API
   */
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    streamId:              String,
    fromPositionExclusive: java.lang.Long,
    credentials:           UserCredentials,
    settings:              Settings
  ): Props = props(
    connection,
    client,
    EventStream.Id(streamId),
    Option(fromPositionExclusive).map(EventNumber.Exact(_)),
    Option(credentials),
    Option(settings).getOrElse(Settings.Default)
  )

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos:      Boolean,
    credentials:         Option[UserCredentials],
    readBatchSize:       Int
  ): Props = {
    val settings = Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos)
    props(connection, client, streamId, fromNumberExclusive, credentials, settings)
  }

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber]
  ): Props = {
    props(connection, client, streamId, fromNumberExclusive, None, Settings.Default)
  }
}

private[eventstore] class StreamSubscriptionActor private (
    val connection:      ActorRef,
    val client:          ActorRef,
    val streamId:        EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    val credentials:     Option[UserCredentials],
    val settings:        Settings
) extends AbstractSubscriptionActor[Event] {

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

      rcvReadCompleted(read) or
        rcvStreamNotFound(subscribing(last, next)) or
        rcvFailure
    }

    readEventsFrom(next)
    rcv(ready) or rcvReady(rcv(ready = true))
  }

  def subscribing(last: Last, next: Next): Receive = {
    def subscribed(subscriptionNumber: Last) = {
      subscriptionNumber match {
        case Some(x) if !last.exists(_ >= x) => catchingUp(last, next, x, Queue())
        case _                               => liveProcessing(last, Queue())
      }
    }

    subscribeToStream()
    rcvSubscribeCompleted(subscribed) or
      rcvFailureOrUnsubscribe
  }

  def subscribingFromLast(): Receive = {
    def subscribed(number: Last) = {
      liveProcessing(number, Queue())
    }

    subscribeToStream()
    rcvSubscribeCompleted(subscribed) or
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
        catchUp(number.getOrElse(subscriptionNumber), Queue())
      }

      rcvEventAppeared(eventAppeared) or
        rcvSubscribeCompleted(subscribed) or
        rcvReadCompleted(read) or
        rcvFailureOrUnsubscribe
    }

    readEventsFrom(next)
    catchUp(subscriptionNumber, stash)
  }

  def liveProcessing(last: Last, stash: Queue[Event]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive = {
      def eventAppeared(event: IndexedEvent) = {
        val l = process(last, event.event)
        if (n < settings.readBatchSize) liveProcessing(l, n + 1, ready)
        else {
          checkReadiness()
          if (ready) liveProcessing(l, 0, ready = false)
          else {
            unsubscribe()
            rcvReady(reading(l, l getOrElse EventNumber.First, ready = false)) or
              ignoreUnsubscribed or
              rcvFailure
          }
        }
      }

      def subscribed(number: Last) = {
        (number, last) match {
          case (Some(nr), Some(l)) if nr > l => catchingUp(Some(l), l, nr, Queue())
          case _                             => liveProcessing(last, n, ready)
        }
      }

      rcvEventAppeared(eventAppeared) or
        rcvReady(liveProcessing(last, n, ready = true)) or
        rcvSubscribeCompleted(subscribed) or
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
    val read = ReadStreamEvents(
      streamId = streamId,
      fromNumber = number,
      maxCount = settings.readBatchSize,
      direction = Forward,
      resolveLinkTos = settings.resolveLinkTos,
      requireMaster = settings.requireMaster
    )
    toConnection(read)
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