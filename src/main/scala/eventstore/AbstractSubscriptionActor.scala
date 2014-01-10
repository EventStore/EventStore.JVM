package eventstore

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorLogging, Actor }
import tcp.ConnectionActor.{ Reconnected, WaitReconnected }

trait AbstractSubscriptionActor[T] extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def resolveLinkTos: Boolean

  context watch client
  context watch connection

  var subscribed = false

  def subscribeToStream() {
    connection ! SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)
  }

  val rcvFailure: Receive = {
    case Failure(EsException(EsError.ConnectionLost, _)) =>
      connection ! WaitReconnected
    case Failure(e) =>
      log.error(e.toString)
      context stop self
  }

  val rcvUnsubscribe: Receive = {
    case UnsubscribeCompleted =>
      subscribed = false
      context stop self
  }

  def rcvReconnected(receive: => Receive): Receive = {
    case Reconnected =>
      subscribed = false
      context become receive
  }

  def forward(event: T) {
    client ! event
  }

  override def postStop() {
    if (subscribed) connection ! Unsubscribe
  }
}

object Subscription {
  case object LiveProcessingStarted
}
