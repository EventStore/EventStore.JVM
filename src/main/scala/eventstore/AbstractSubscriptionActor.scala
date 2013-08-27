package eventstore

import akka.actor.{ SupervisorStrategy, ActorRef, ActorLogging, Actor }

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

  def subscriptionFailed(msg: => String): Receive = {
    case SubscriptionDropped(reason) =>
      subscribed = false
      log.warning(s"$streamId: subscription failed: $reason, $msg")
      EventStore.error(reason)
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
