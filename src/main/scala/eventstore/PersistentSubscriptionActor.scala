package eventstore
import akka.actor.Status.Failure
import akka.actor.{ ActorRef, Props }
import eventstore.PersistentSubscription.{ Ack, EventAppeared }
import eventstore.ReadDirection.Forward
import eventstore.{ PersistentSubscription => PS }

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object PersistentSubscriptionActor {
  def props(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    groupName:           String,
    fromNumberExclusive: Option[EventNumber],
    credentials:         Option[UserCredentials],
    settings:            Settings,
    autoAck:             Boolean                 = true
  ): Props = {
    Props(new PersistentSubscriptionActor(
      connection,
      client,
      streamId,
      groupName,
      fromNumberExclusive,
      credentials,
      settings,
      autoAck
    ))
  }
}

class PersistentSubscriptionActor private (
    val connection:      ActorRef,
    val client:          ActorRef,
    val streamId:        EventStream,
    val groupName:       String,
    fromNumberExclusive: Option[EventNumber],
    val credentials:     Option[UserCredentials],
    val settings:        Settings,
    val autoAck:         Boolean
) extends AbstractPersistentSubscriptionActor[Event] {

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  var subscriptionId: Option[String] = None

  def receive = fromNumberExclusive match {
    case Some(EventNumber.Last)     => subscribingFromLast()
    case Some(x: EventNumber.Exact) => reading(Some(x), x, ready = true)
    case None                       => reading(None, EventNumber.First, ready = true)
  }

  def subscribingFromLast(): Receive = {
    def subscribed(eventNumber: Last): Receive = {
      liveProcessing(eventNumber, Queue())
    }

    subscribeToPersistentStream()
    rcvSubscribeCompleted(subscribed) or
      rcvFailureOrUnsubscribe
  }

  def liveProcessing(last: Last, stash: Queue[Event]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive = {
      def eventAppeared(event: Event) = {
        val l = process(last, event)
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

      def subscribed(number: Last): Receive = {
        (number, last) match {
          case (Some(number), Some(last)) if number > last => catchingUp(Some(last), last, number, Queue())
          case _ => liveProcessing(last, n, ready)
        }
      }

      receiveEventAppeared(eventAppeared) or
        rcvReady(liveProcessing(last, n, ready = true)) or
        rcvSubscribeCompleted(subscribed) or
        rcvFailureOrUnsubscribe
    }

    client ! LiveProcessingStarted
    liveProcessing(process(last, stash), 0, ready = true)
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

      def eventAppeared(event: Event) = {
        catchUp(subscriptionNumber, stash enqueue event)
      }

      def subscribed(number: Last) = {
        catchUp(subscriptionNumber, Queue())
      }

      receiveEventAppeared(eventAppeared) or
        rcvSubscribeCompleted(subscribed) or
        rcvReadCompleted(read) or
        rcvFailureOrUnsubscribe
    }

    readEventsFrom(next)
    catchUp(subscriptionNumber, stash)
  }

  override def process(last: Last, event: Event): Last = {
    val number = event.record.number
    if (last.exists(_ >= number)) last
    else {
      toClient(event)
      Some(number)
    }
  }

  def readEventsFrom(number: Next): Unit = {
    val read = ReadStreamEvents(
      streamId = EventStream.Id(streamId.streamId),
      fromNumber = number,
      maxCount = settings.readBatchSize,
      direction = Forward,
      resolveLinkTos = settings.resolveLinkTos,
      requireMaster = settings.requireMaster
    )
    toConnection(read)
  }

  def rcvReadCompleted(receive: (List[Event], Next, Boolean) => Receive): Receive = {
    case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) =>
      if (autoAck && this.subscriptionId.nonEmpty) toConnection(Ack(this.subscriptionId.get, events.map(_.data.eventId)))
      context become receive(events, n, endOfStream)
  }

  def rcvSubscribeCompleted(receive: Last => Receive): Receive = {
    case PS.Connected(subId, _, eventNum) =>
      this.subscriptionId = Some(subId)
      context become receive(eventNum)
  }

  def subscribing(last: Last, next: Next): Receive = {
    def subscribed(subscriptionNumber: Last) = {
      subscriptionNumber match {
        case Some(x) if !last.exists(_ >= x) => catchingUp(last, next, x, Queue())
        case _                               => liveProcessing(last, Queue())
      }
    }

    subscribeToPersistentStream()
    rcvSubscribeCompleted(subscribed) or
      rcvFailureOrUnsubscribe
  }

  def rcvStreamNotFound(receive: => Receive): Receive = {
    case Failure(_: StreamNotFoundException) => context become receive
  }

  def receiveEventAppeared(receive: (Event) => Receive): Receive = {
    case EventAppeared(x) =>
      if (autoAck && this.subscriptionId.nonEmpty) toConnection(Ack(this.subscriptionId.get, x.data.eventId :: Nil))
      context become receive(x)
  }
}
