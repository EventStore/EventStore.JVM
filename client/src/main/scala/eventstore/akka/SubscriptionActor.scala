package eventstore
package akka

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import _root_.akka.actor.{ActorRef, Props}
import ReadDirection.Forward

object SubscriptionActor {

  def props(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Option[Position],
    credentials:           Option[UserCredentials],
    settings:              Settings
  ): Props = {

    Props(new SubscriptionActor(
      connection = connection,
      client = client,
      fromPositionExclusive = fromPositionExclusive,
      credentials = credentials,
      settings = settings
    ))
  }

  /**
   * Java API
   */
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Position.Exact,
    credentials:           UserCredentials,
    settings:              Settings
  ): Props = props(
    connection,
    client,
    Option(fromPositionExclusive),
    Option(credentials),
    Option(settings).getOrElse(Settings.Default)
  )

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Option[Position],
    resolveLinkTos:        Boolean,
    credentials:           Option[UserCredentials],
    readBatchSize:         Int
  ): Props = {
    val settings  = Settings.Default.copy(resolveLinkTos = resolveLinkTos, readBatchSize = readBatchSize)
    props(connection, client, fromPositionExclusive, credentials, settings)
  }

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(connection: ActorRef, client: ActorRef, fromPositionExclusive: Option[Position]) = {
    props(connection, client, fromPositionExclusive, None, Settings.Default)
  }

}

private[eventstore] class SubscriptionActor(
    val connection:        ActorRef,
    val client:            ActorRef,
    fromPositionExclusive: Option[Position],
    val credentials:       Option[UserCredentials],
    val settings:          Settings
) extends AbstractSubscriptionActor[IndexedEvent] {

  type Next = Position.Exact
  type Last = Option[Position.Exact]

  val streamId = EventStream.All

  def receive = fromPositionExclusive match {
    case Some(Position.Last)     => subscribingFromLast()
    case Some(x: Position.Exact) => reading(Some(x), x, ready = true)
    case None                    => reading(None, Position.First, ready = true)
  }

  def reading(last: Last, next: Next, ready: Boolean): Receive = {
    def rcv(ready: Boolean) = {
      def read(events: List[IndexedEvent], next: Position.Exact) = {
        if (events.isEmpty) subscribing(last, next)
        else {
          val l = process(last, events)
          whenReady(reading(l, next, ready = false), ready)
        }
      }

      rcvReadCompleted(read) or rcvFailure
    }

    readEventsFrom(next)
    rcv(ready) or rcvReady(rcv(ready = true))
  }

  def subscribing(last: Last, next: Next): Receive = {
    def subscribed(position: Long) = {
      if (last.exists(_.commitPosition >= position)) liveProcessing(last, Queue())
      else catchingUp(last, next, position, Queue())
    }

    subscribeToStream()
    rcvSubscribeCompleted(subscribed) or
      rcvFailureOrUnsubscribe
  }

  def subscribingFromLast(): Receive = {
    def subscribed: Receive =
      liveProcessing(None, Queue())

    subscribeToStream()
    rcvSubscribeCompleted(_ ⇒ subscribed) or
      rcvFailureOrUnsubscribe
  }

  def catchingUp(last: Last, next: Next, subscriptionCommit: Long, stash: Queue[IndexedEvent]): Receive = {
    def catchUp(subscriptionCommit: Long, stash: Queue[IndexedEvent]): Receive = {
      def read(events: List[IndexedEvent], next: Position.Exact) = {
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
      }

      def eventAppeared(event: IndexedEvent) = {
        catchUp(subscriptionCommit, stash enqueue event)
      }

      def subscribed(position: Long) = {
        catchUp(position, Queue())
      }

      rcvReadCompleted(read) or
        rcvEventAppeared(eventAppeared) or
        rcvSubscribeCompleted(subscribed) or
        rcvFailureOrUnsubscribe
    }

    readEventsFrom(next)
    catchUp(subscriptionCommit, stash)
  }

  def liveProcessing(last: Last, stash: Queue[IndexedEvent]): Receive = {
    def liveProcessing(last: Last, n: Long, ready: Boolean): Receive = {
      def eventAppeared(event: IndexedEvent) = {
        val l = process(last, event)
        if (n < settings.readBatchSize) liveProcessing(l, n + 1, ready)
        else {
          checkReadiness()
          if (ready) liveProcessing(l, 0, ready = false)
          else {
            unsubscribe()
            rcvReady(reading(l, l getOrElse Position.First, ready = false)) or
              ignoreUnsubscribed or
              rcvFailure
          }
        }
      }

      def subscribed(position: Long) = {
        last match {
          case Some(l) if position > l.commitPosition => catchingUp(Some(l), l, position, Queue())
          case _                                      => liveProcessing(last, n, ready)
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

  def process(lastPosition: Option[Position.Exact], event: IndexedEvent): Last = {
    val position = event.position
    if (lastPosition.exists(_ >= position)) lastPosition
    else {
      toClient(event)
      Some(position)
    }
  }

  def readEventsFrom(position: Next) = {
    val msg = ReadAllEvents(
      position, settings.readBatchSize,
      Forward,
      resolveLinkTos = settings.resolveLinkTos,
      requireMaster = settings.requireMaster
    )
    toConnection(msg)
  }

  def rcvReadCompleted(receive: (List[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsCompleted(events, _, next, Forward) => context become receive(events, next)
  }

  def rcvSubscribeCompleted(receive: Long => Receive): Receive = {
    case SubscribeToAllCompleted(x) => context become receive(x)
  }
}