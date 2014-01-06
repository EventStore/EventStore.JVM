package eventstore

import akka.actor.{ SupervisorStrategy, ActorRef, ActorLogging, Actor }
import akka.actor.Status.Failure

trait AbstractSubscriptionActor extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def resolveLinkTos: Boolean

  context watch client
  context watch connection

  var subscribed = false

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def subscribeToStream(at: AnyRef) {
    debug(s"subscribing at {}", at)
    connection ! SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)
  }

  def subscriptionFailed: Receive = {
    case UnsubscribeCompleted =>
      subscribed = false
      context stop self

    case Failure(e) =>
      subscribed = false
      throw e
  }

  def debug(msg: => String) {
    log.debug("{}: {}", streamId, msg)
  }

  def debug(msg: => String, arg: Any) {
    if (log.isDebugEnabled) log.debug(s"{}: $msg", streamId, arg)
  }

  def debug(msg: => String, arg1: Any, arg2: Any) {
    if (log.isDebugEnabled) log.debug(s"{}: $msg", streamId, arg1, arg2)
  }

  override def postStop() {
    if (subscribed) {
      debug("unsubscribing")
      connection ! UnsubscribeFromStream
    }
  }
}

object Subscription {
  case object LiveProcessingStarted
}
