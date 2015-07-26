package eventstore

import akka.actor.{ ActorRef, Props }
import eventstore.ReadDirection.Forward

import scala.collection.immutable.Queue

object StreamPublisher {
  def props(
    connection: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber] = None,
    resolveLinkTos: Boolean = Settings.Default.resolveLinkTos,
    credentials: Option[UserCredentials] = None,
    readBatchSize: Int = Settings.Default.readBatchSize): Props = {

    Props(new StreamPublisher(
      connection,
      streamId,
      fromNumberExclusive,
      resolveLinkTos,
      credentials,
      readBatchSize))
  }

  /**
   * Java API
   */
  def getProps(
    connection: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos: Boolean,
    credentials: Option[UserCredentials],
    readBatchSize: Int): Props = {

    props(connection, streamId, fromNumberExclusive, resolveLinkTos, credentials, readBatchSize)
  }

  /**
   * Java API
   */
  def getProps(
    connection: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber]): Props = {

    props(connection, streamId, fromNumberExclusive)
  }
}

class StreamPublisher(
    val connection: ActorRef,
    val streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    val resolveLinkTos: Boolean,
    val credentials: Option[UserCredentials],
    val readBatchSize: Int) extends AbstractStreamPublisher[Event, EventNumber, EventNumber.Exact] {

  var last = fromNumberExclusive collect { case x: EventNumber.Exact => x }

  def receive = fromNumberExclusive match {
    case Some(EventNumber.Last)     => subscribingFromLast
    case Some(x: EventNumber.Exact) => reading(x)
    case None                       => reading(first)
  }

  def subscribing(next: Next): Receive = {
    def subscribed(subscriptionNumber: Last) = subscriptionNumber
      .filter { subscriptionNumber => last map { _ < subscriptionNumber } getOrElse true }
      .map { subscriptionNumber => catchingUp(next, subscriptionNumber, Queue()) }
      .getOrElse { this.subscribed }

    subscribeToStream()
    rcvSubscribed(subscribed) or rcvUnsubscribed or rcvRequest() or rcvCancel or rcvFailure
  }

  def catchingUp(next: Next, subscriptionNumber: Next, stash: Queue[Event]): Receive = {

    def catchUp(subscriptionNumber: Next, stash: Queue[Event]): Receive = {

      def read(events: List[Event], next: Next): Receive = {
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

      rcvRead(read) or
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
    val read = ReadStreamEvents(streamId, next, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
    toConnection(read)
  }

  def rcvRead(receive: (List[Event], EventNumber.Exact) => Receive): Receive = {
    case ReadStreamEventsCompleted(events, n: Next, _, endOfStream, _, Forward) =>
      context become receive(events, n)
  }

  def rcvSubscribed(receive: Last => Receive): Receive = {
    case SubscribeToStreamCompleted(_, subscriptionNumber) => context become receive(subscriptionNumber)
  }

  def subscribingFromLast: Receive = {
    subscribeToStream()
    rcvSubscribed(_ => subscribed) or rcvUnsubscribed or rcvRequest() or rcvCancel or rcvFailure
  }

  def position(event: Event) = event.record.number

  def first = EventNumber.First
}
