package eventstore

import akka.actor._
import eventstore.ReadDirection.Forward

import scala.collection.immutable.Queue

object AllStreamsPublisher {

  @deprecated("Use `props` with Settings as argument", "2.2")
  def props(
    connection:            ActorRef,
    fromPositionExclusive: Option[Position]        = None,
    resolveLinkTos:        Boolean                 = Settings.Default.resolveLinkTos,
    credentials:           Option[UserCredentials] = None,
    infinite:              Boolean                 = true,
    readBatchSize:         Int                     = Settings.Default.readBatchSize
  ): Props = {

    props(
      connection = connection,
      fromPositionExclusive = fromPositionExclusive,
      credentials = credentials,
      settings = Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos),
      infinite = infinite
    )
  }

  def props(
    connection:            ActorRef,
    fromPositionExclusive: Option[Position],
    credentials:           Option[UserCredentials],
    settings:              Settings,
    infinite:              Boolean
  ): Props = {

    Props(new AllStreamsPublisher(
      connection = connection,
      fromPositionExclusive = fromPositionExclusive,
      credentials = credentials,
      settings = settings,
      infinite = infinite
    ))
  }

  /**
   * Java API
   */
  def getProps(
    connection:            ActorRef,
    fromPositionExclusive: Option[Position],
    resolveLinkTos:        Boolean,
    credentials:           Option[UserCredentials],
    infinite:              Boolean,
    readBatchSize:         Int
  ): Props = {

    props(connection, fromPositionExclusive, resolveLinkTos, credentials, infinite, readBatchSize)
  }

  /**
   * Java API
   */
  def getProps(
    connection:            ActorRef,
    fromPositionExclusive: Option[Position],
    credentials:           Option[UserCredentials],
    settings:              Settings,
    infinite:              Boolean
  ): Props = {

    props(
      connection = connection,
      fromPositionExclusive = fromPositionExclusive,
      credentials = credentials,
      settings = settings,
      infinite = infinite
    )
  }

  /**
   * Java API
   */
  def getProps(connection: ActorRef, fromPositionExclusive: Option[Position]) = {
    props(connection, fromPositionExclusive)
  }
}

private class AllStreamsPublisher(
    val connection:        ActorRef,
    fromPositionExclusive: Option[Position],
    val credentials:       Option[UserCredentials],
    val settings:          Settings,
    val infinite:          Boolean                 = true
) extends AbstractStreamPublisher[IndexedEvent, Position, Position.Exact] {

  val streamId = EventStream.All
  var last = fromPositionExclusive collect { case x: Position.Exact => x }

  def receive = fromPositionExclusive match {
    case Some(Position.Last)     => subscribingFromLast
    case Some(x: Position.Exact) => reading(x)
    case None                    => reading(first)
  }

  def subscribingFromLast: Receive = {
    if (infinite) {
      subscribeToStream()
      rcvSubscribed(_ => subscribed) or rcvUnsubscribed or rcvRequest() or rcvCancel or rcvFailure
    } else {
      onCompleteThenStop()
      PartialFunction.empty
    }
  }

  def subscribing(next: Next): Receive = {
    def subscribed(position: Long) = last
      .collect { case last if position <= last.commitPosition => this.subscribed }
      .getOrElse { catchingUp(next, position, Queue()) }

    subscribeToStream()
    rcvSubscribed(subscribed) or rcvUnsubscribed or rcvRequest() or rcvCancel or rcvFailure
  }

  def catchingUp(next: Next, subscriptionCommit: Long, stash: Queue[IndexedEvent]): Receive = {

    def catchUp(subscriptionCommit: Long, stash: Queue[IndexedEvent]): Receive = {

      def read(events: List[IndexedEvent], next: Position.Exact): Receive = {
        enqueue(events)
        if (events.isEmpty || (events exists { _.position.commitPosition > subscriptionCommit })) {
          enqueue(stash)
          subscribed
        } else {
          if (ready) catchingUp(next, subscriptionCommit, stash)
          else unsubscribing
        }
      }

      def eventAppeared(event: IndexedEvent) = {
        catchUp(subscriptionCommit, stash enqueue event)
      }

      rcvRead(next, read) or
        rcvEventAppeared(eventAppeared) or
        rcvUnsubscribed or
        rcvRequest() or
        rcvCancel or
        rcvFailure
    }

    readEventsFrom(next)
    catchUp(subscriptionCommit, stash)
  }

  def subscribed: Receive = {
    def eventAppeared(event: IndexedEvent) = {
      enqueue(event)
      if (ready) subscribed else unsubscribing
    }

    rcvEventAppeared(eventAppeared) or
      rcvUnsubscribed or
      rcvRequest() or
      rcvCancel or
      rcvFailure
  }

  def readEventsFrom(next: Next) = {
    val msg = ReadAllEvents(
      fromPosition = next,
      maxCount = settings.readBatchSize,
      direction = Forward,
      resolveLinkTos = settings.resolveLinkTos,
      requireMaster = settings.requireMaster
    )
    toConnection(msg)
  }

  def rcvRead(next: Next, receive: (List[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsCompleted(events, _, next, Forward) =>
      context become receive(events, next)
  }

  def rcvSubscribed(receive: Long => Receive): Receive = {
    case SubscribeToAllCompleted(x) => context become receive(x)
  }

  def position(event: IndexedEvent) = event.position

  def first = Position.First
}