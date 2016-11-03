package eventstore

import akka.actor.{ ActorRef, Props, Status }
import eventstore.ReadDirection.Forward

import scala.collection.immutable.Queue

object StreamPublisher {
  @deprecated("Use `props` with Settings as argument", "2.2")
  def props(
    connection:          ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber]     = None,
    resolveLinkTos:      Boolean                 = Settings.Default.resolveLinkTos,
    credentials:         Option[UserCredentials] = None,
    infinite:            Boolean                 = true,
    readBatchSize:       Int                     = Settings.Default.readBatchSize
  ): Props = {

    props(
      connection = connection,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      credentials = credentials,
      settings = Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos),
      infinite = infinite
    )
  }

  def props(
    connection:          ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    credentials:         Option[UserCredentials],
    settings:            Settings,
    infinite:            Boolean
  ): Props = {

    Props(new StreamPublisher(
      connection = connection,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      credentials = credentials,
      settings = settings,
      infinite = infinite
    ))
  }

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos:      Boolean,
    credentials:         Option[UserCredentials],
    infinite:            Boolean,
    readBatchSize:       Int
  ): Props = {

    props(connection, streamId, fromNumberExclusive, resolveLinkTos, credentials, infinite, readBatchSize)
  }

  /**
   * Java API
   */
  def getProps(
    connection:          ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    credentials:         Option[UserCredentials] = None,
    settings:            Settings,
    infinite:            Boolean
  ): Props = {

    props(
      connection = connection,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      credentials = credentials,
      settings = settings,
      infinite = infinite
    )
  }

  /**
   * Java API
   */
  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber]
  ): Props = {

    props(
      connection = connection,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      credentials = None,
      settings = Settings.Default,
      infinite = true
    )
  }
}

private class StreamPublisher(
    val connection:      ActorRef,
    val streamId:        EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    val credentials:     Option[UserCredentials],
    val settings:        Settings,
    val infinite:        Boolean                 = true
) extends AbstractStreamPublisher[Event, EventNumber, EventNumber.Exact] {

  var last = fromNumberExclusive collect { case x: EventNumber.Exact => x }

  def receive = fromNumberExclusive match {
    case Some(EventNumber.Last)     => subscribingFromLast
    case Some(x: EventNumber.Exact) => reading(x)
    case None                       => reading(first)
  }

  def subscribing(next: Next): Receive = {
    def subscribed(subscriptionNumber: Last) = subscriptionNumber
      .filter { subscriptionNumber => last forall { _ < subscriptionNumber } }
      .map { subscriptionNumber => catchingUp(next, subscriptionNumber, Queue()) }
      .getOrElse { this.subscribed }

    subscribeToStream()
    rcvSubscribed(subscribed) or rcvUnsubscribed or rcvRequest() or rcvCancel or rcvFailure
  }

  def catchingUp(next: Next, subscriptionNumber: Next, stash: Queue[Event]): Receive = {

    def catchUp(subscriptionNumber: Next, stash: Queue[Event]): Receive = {

      def read(events: List[Event], next: Next, endOfStream: Boolean): Receive = {
        enqueue(events)
        if (events.isEmpty || (events exists { event => position(event) > subscriptionNumber })) {
          enqueue(stash)
          subscribed
        } else {
          if (ready) catchingUp(next, subscriptionNumber, stash)
          else unsubscribing
        }
      }

      def eventAppeared(event: IndexedEvent) = {
        catchUp(subscriptionNumber, stash enqueue event.event)
      }

      rcvRead(next, read) or
        rcvEventAppeared(eventAppeared) or
        rcvUnsubscribed or
        rcvRequest() or
        rcvCancel or
        rcvFailure
    }

    readEventsFrom(next)
    catchUp(subscriptionNumber, stash)
  }

  def subscribed: Receive = {
    def eventAppeared(event: IndexedEvent) = {
      enqueue(event.event)
      if (ready) subscribed else unsubscribing
    }

    rcvEventAppeared(eventAppeared) or
      rcvUnsubscribed or
      rcvRequest() or
      rcvCancel or
      rcvFailure
  }

  def readEventsFrom(next: Next) = {
    val read = ReadStreamEvents(
      streamId = streamId,
      fromNumber = next,
      maxCount = settings.readBatchSize,
      direction = Forward,
      resolveLinkTos = settings.resolveLinkTos,
      requireMaster = settings.requireMaster
    )
    toConnection(read)
  }

  def rcvRead(next: Next, receive: (List[Event], EventNumber.Exact, Boolean) => Receive): Receive = {
    case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) =>
      context become receive(events, n, endOfStream)

    case Status.Failure(_: StreamNotFoundException) =>
      context become receive(Nil, next, true)
  }

  def rcvSubscribed(receive: Last => Receive): Receive = {
    case SubscribeToStreamCompleted(_, subscriptionNumber) => context become receive(subscriptionNumber)
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

  def position(event: Event) = event.record.number

  def first = EventNumber.First
}
