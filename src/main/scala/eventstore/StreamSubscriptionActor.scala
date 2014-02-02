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
    credentials: Option[UserCredentials] = None,
    readBatchSize: Int = 100): Props = Props(classOf[StreamSubscriptionActor], connection, client, streamId,
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
    readEventsFrom(next)
    def rcv(ready: Boolean): Receive = {
      val receive: Receive = {
        case Failure(EsException(EsError.StreamNotFound, _)) => context become subscribing(last, next)
        case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) => context become {
          val l = process(last, events)
          if (endOfStream) subscribing(l, n)
          else whenReady(reading(l, n, ready = false), ready)
        }
      }
      receive orElse rcvFailure orElse rcvReconnected(reading(last, next, ready = true))
    }
    rcv(ready) orElse rcvReady(rcv(ready = true))
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

    def catchUp(stash: Queue[Event]): Receive = rcvFailureOrUnsubscribe orElse rcvReconnected(last, next) orElse
      rcvEventAppeared(x => catchUp(stash enqueue x.event)) orElse {
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
                  toClient(event)
                  loop(tail, Some(number))
                }
            }
            loop(events, last)
          })
      }

    catchUp(stash)
  }

  def liveProcessing(last: Last, stash: Queue[Event]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive =
      rcvFailureOrUnsubscribe orElse rcvReconnected(last, last getOrElse EventNumber.First) orElse rcvEventAppeared {
        x =>
          val l = process(last, x.event)
          if (n < readBatchSize) liveProcessing(l, n + 1, ready)
          else {
            checkReadiness()
            if (ready) liveProcessing(l, 0, ready = false)
            else {
              unsubscribe()
              rcvFailure orElse rcvReady(reading(l, l getOrElse EventNumber.First, ready = false)) orElse {
                case UnsubscribeCompleted => subscribed = false
              }
            }
          }
      } orElse rcvReady(liveProcessing(last, n, ready = true))

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

  def readEventsFrom(number: Next) {
    toConnection(ReadStreamEvents(streamId, number, readBatchSize, Forward, resolveLinkTos = resolveLinkTos))
  }

  def rcvSubscribeCompleted(receive: Last => Receive): Receive = {
    case SubscribeToStreamCompleted(_, subscriptionNumber) =>
      subscribed = true
      context become receive(subscriptionNumber)
  }
}