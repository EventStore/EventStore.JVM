package eventstore

import akka.actor.{ SupervisorStrategy, ActorRef, ActorLogging, Actor }
import akka.actor.Status.Failure

/**
 * @author Yaroslav Klymko
 */
trait AbstractSubscriptionActor extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def resolveLinkTos: Boolean

  context watch client
  context watch connection

  var subscribed = false

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def subscribeToStream(msg: => String) {
    debug(s"subscribing: $msg")
    connection ! SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)
  }

  def subscriptionFailed: Receive = {
    case UnsubscribeCompleted =>
      subscribed = false
      context stop self

    case Failure(e: EventStoreException) =>
      subscribed = false
      throw e
  }

  def debug(msg: => String) {
    log.debug(s"$streamId: $msg")
  }

  override def postStop() {
    if (subscribed) {
      debug("unsubscribing")
      connection ! UnsubscribeFromStream
    }
  }
}
