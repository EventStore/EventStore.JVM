package eventstore

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorLogging, Actor }
import tcp.ConnectionActor.{ Reconnected, WaitReconnected }

trait AbstractSubscriptionActor[T] extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def resolveLinkTos: Boolean
  def credentials: Option[UserCredentials]

  type Next
  type Last

  context watch client
  context watch connection

  var subscribed = false

  val rcvFailure: Receive = {
    case Failure(EsException(EsError.ConnectionLost, _)) => connection ! WaitReconnected
    case failure @ Failure(e) =>
      log.error(e.toString)
      client ! failure
      context stop self
  }

  val rcvFailureOrUnsubscribe: Receive = rcvFailure orElse {
    case UnsubscribeCompleted =>
      subscribed = false
      context stop self
  }

  def rcvReconnected(receive: => Receive): Receive = {
    case Reconnected =>
      subscribed = false
      context become receive
  }

  def rcvReconnected(last: Last, next: Next): Receive = rcvReconnected(subscribing(last, next))

  def subscribing(last: Last, next: Next): Receive

  def process(last: Last, events: Seq[T]): Last = events.foldLeft(last)(process)

  def process(last: Last, event: T): Last

  def toClient(event: T) {
    client ! event
  }

  def toConnection(x: Out) {
    connection ! credentials.fold[OutLike](x)(x.withCredentials)
  }

  def subscribeToStream() {
    toConnection(SubscribeTo(streamId, resolveLinkTos = resolveLinkTos))
  }

  override def postStop() {
    if (subscribed) toConnection(Unsubscribe)
  }
}

object Subscription {
  case object LiveProcessingStarted
}
