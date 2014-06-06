package eventstore

import ReadDirection.Forward
import akka.actor.{ Props, ActorRef }
import scala.collection.immutable.Queue
import scala.annotation.tailrec

object SubscriptionActor {
  def props(
    connection: ActorRef,
    client: ActorRef,
    fromPositionExclusive: Option[Position] = None,
    resolveLinkTos: Boolean = false,
    credentials: Option[UserCredentials] = None,
    readBatchSize: Int = Settings.ReadBatchSize): Props = Props(classOf[SubscriptionActor], connection, client,
    fromPositionExclusive, resolveLinkTos, credentials, readBatchSize)

  /**
   * Java API
   */
  def getProps(
    connection: ActorRef,
    client: ActorRef,
    fromPositionExclusive: Option[Position],
    resolveLinkTos: Boolean,
    credentials: Option[UserCredentials],
    readBatchSize: Int): Props = props(connection, client, fromPositionExclusive, resolveLinkTos,
    credentials, readBatchSize)

  /**
   * Java API
   */
  def getProps(connection: ActorRef, client: ActorRef, fromPositionExclusive: Option[Position]) =
    props(connection, client, fromPositionExclusive)
}

class SubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    fromPositionExclusive: Option[Position],
    val resolveLinkTos: Boolean,
    val credentials: Option[UserCredentials],
    val readBatchSize: Int) extends AbstractSubscriptionActor[IndexedEvent] {

  type Next = Position.Exact
  type Last = Option[Position.Exact]

  val streamId = EventStream.All

  def receive = fromPositionExclusive match {
    case Some(Position.Last)     => subscribingFromLast()
    case Some(x: Position.Exact) => reading(Some(x), x, ready = true)
    case None                    => reading(None, Position.First, ready = true)
  }

  def reading(last: Last, next: Next, ready: Boolean): Receive = {
    readEventsFrom(next)
    def rcv(ready: Boolean) = rcvFailure orElse rcvReconnected(reading(last, next, ready = true)) orElse rcvReadCompleted {
      (events, next) =>
        if (events.isEmpty) subscribing(last, next)
        else {
          val l = process(last, events)
          whenReady(reading(l, next, ready = false), ready)
        }
    }
    rcv(ready) orElse rcvReady(rcv(ready = true))
  }

  def subscribing(last: Last, next: Next): Receive = {
    subscribeToStream()
    rcvFailureOrUnsubscribe orElse rcvReconnected(last, next) orElse rcvSubscribeCompleted {
      lastCommit =>
        if (last.exists(_.commitPosition >= lastCommit)) liveProcessing(last, Queue())
        else catchingUp(last, next, lastCommit, Queue())
    }
  }

  def subscribingFromLast(): Receive = {
    subscribeToStream()
    rcvFailureOrUnsubscribe orElse rcvReconnected(subscribingFromLast()) orElse rcvSubscribeCompleted {
      lastCommit => liveProcessing(None, Queue())
    }
  }

  def catchingUp(last: Last, next: Next, subscriptionCommit: Long, stash: Queue[IndexedEvent]): Receive = {
    def catchUp(stash: Queue[IndexedEvent]): Receive = rcvReadCompleted {
      (events, next) =>
        if (events.isEmpty) liveProcessing(last, stash)
        else {
          @tailrec def loop(events: List[IndexedEvent], last: Last): Receive = events match {
            case Nil => catchingUp(last, next, subscriptionCommit, stash)
            case event :: tail =>
              val position = event.position
              if (last.exists(_ >= position)) loop(tail, last)
              else if (position.commitPosition > subscriptionCommit) liveProcessing(last, stash)
              else {
                toClient(event)
                loop(tail, Some(position))
              }
          }
          loop(events, last)
        }
    } orElse rcvFailureOrUnsubscribe orElse rcvReconnected(last, next) orElse rcvEventAppeared {
      x => catchUp(stash enqueue x)
    }

    readEventsFrom(next)
    catchUp(stash)
  }

  def liveProcessing(last: Last, stash: Queue[IndexedEvent]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive =
      rcvFailureOrUnsubscribe orElse rcvReconnected(last, last getOrElse Position.First) orElse rcvEventAppeared {
        x =>
          val l = process(last, x)
          if (n < readBatchSize) liveProcessing(l, n + 1, ready)
          else {
            checkReadiness()
            if (ready) liveProcessing(l, 0, ready = false)
            else {
              unsubscribe()
              rcvFailure orElse rcvReady(reading(l, l getOrElse Position.First, ready = false)) orElse {
                case UnsubscribeCompleted => subscribed = false
              }
            }
          }
      } orElse rcvReady(liveProcessing(last, n, ready = true))
    client ! LiveProcessingStarted
    liveProcessing(process(last, stash), 0, ready = true)
  }

  def process(lastPosition: Option[Position.Exact], event: IndexedEvent): Last = {
    val position = event.position
    if (lastPosition.exists(_ >= position)) lastPosition
    else {
      toClient(event)
      Some(position)
    }
  }

  def readEventsFrom(position: Next) {
    toConnection(ReadAllEvents(position, readBatchSize, Forward, resolveLinkTos = resolveLinkTos))
  }

  def rcvReadCompleted(f: (List[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsCompleted(events, _, next, Forward) => context become f(events, next)
  }

  def rcvSubscribeCompleted(receive: Long => Receive): Receive = {
    case SubscribeToAllCompleted(lastCommit) =>
      subscribed = true
      context become receive(lastCommit)
  }
}