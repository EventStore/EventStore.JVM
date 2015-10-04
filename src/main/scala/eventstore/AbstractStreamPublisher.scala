package eventstore

import akka.actor.ActorRef
import akka.actor.Status.Failure
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

import scala.collection.mutable

private[eventstore] abstract class AbstractStreamPublisher[T, O <: Ordered[O], P <: O] extends ActorPublisher[T] {
  type Next = P
  type Last = Option[P]

  def streamId: EventStream
  def connection: ActorRef
  def resolveLinkTos: Boolean
  def credentials: Option[UserCredentials]
  def readBatchSize: Int
  def position(event: T): P
  def first: P
  def infinite: Boolean

  context watch connection

  var last: Last
  val buffer = mutable.Queue.empty[T]

  def ready = buffer.size <= readBatchSize

  def enqueue(events: Iterable[T]): Unit = {
    for { event <- events } enqueue(event)
  }

  def enqueue(event: T): Unit = {
    if (totalDemand == 0) buffer enqueue event else onNext(event)
  }

  def toConnection(x: Out) = connection ! credentials.fold[OutLike](x)(x.withCredentials)

  def subscribeToStream() = toConnection(SubscribeTo(streamId, resolveLinkTos = resolveLinkTos))

  def unsubscribe() = toConnection(Unsubscribe)

  val rcvCancel: Receive = {
    case Cancel => context stop self
  }

  override def onNext(element: T) = {
    val p = position(element)
    if (last map { _ < p } getOrElse true) {
      last = Some(p)
      super.onNext(element)
    }
  }

  def reading(next: Next): Receive = {
    def read(events: List[T], next: Next): Receive = {
      enqueue(events)
      if (events.isEmpty) {
        if (infinite) subscribing(next)
        else replyingBuffered()
      } else if (ready) reading(next)
      else rcvRequest(reading(next)) or rcvCancel or rcvFailure
    }

    readEventsFrom(next)
    rcvRead(next, read) or rcvRequest() or rcvCancel or rcvFailure
  }

  def replyingBuffered(): Receive = {
    def rcvRequest: Receive = {
      case Request(n) =>
        dequeue()
        context become replyingBuffered
    }

    if (buffer.nonEmpty) rcvRequest
    else {
      onCompleteThenStop()
      PartialFunction.empty
    }
  }

  def readEventsFrom(next: Next): Unit

  def rcvRead(next: Next, receive: (List[T], Next) => Receive): Receive

  def subscribing(next: Next): Receive

  def subscribingFromLast: Receive

  def subscribed: Receive

  def unsubscribing: Receive = {
    def unsubscribed = {
      def reading = this.reading(last getOrElse first)
      if (ready) reading
      else rcvRequest(reading) or rcvCancel or rcvFailure
    }
    unsubscribe()
    rcvUnsubscribed(unsubscribed) or rcvRequest() or rcvCancel or rcvFailure
  }

  val rcvFailure: Receive = {
    case Failure(failure) => onErrorThenStop(failure)
  }

  def rcvUnsubscribed(receive: => Receive): Receive = {
    case Unsubscribed => context become receive
  }

  def rcvUnsubscribed(): Receive = {
    case Unsubscribed => context stop self
  }

  def rcvEventAppeared(receive: IndexedEvent => Receive): Receive = {
    case StreamEventAppeared(x) => context become receive(x)
  }

  def rcvRequest(): Receive = {
    case Request(n) => dequeue()
  }

  def rcvRequest(receive: => Receive): Receive = {
    case Request(n) =>
      dequeue()
      if (ready) context become receive
  }

  def dequeue() = {
    while (totalDemand > 0 && buffer.nonEmpty) {
      val event = buffer.dequeue()
      onNext(event)
    }
  }
}
