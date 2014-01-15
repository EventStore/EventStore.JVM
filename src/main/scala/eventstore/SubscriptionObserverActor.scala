package eventstore

import Subscription.LiveProcessingStarted
import akka.actor.Status.Failure
import akka.actor.{ Props, Actor }
import scala.reflect.ClassTag
import util.ActorCloseable

object SubscriptionObserverActor {
  def props[T](observer: SubscriptionObserver[T])(implicit tag: ClassTag[T]): Props =
    Props(classOf[SubscriptionObserverActor[T]], observer, tag)
}

class SubscriptionObserverActor[T](observer: SubscriptionObserver[T], tag: ClassTag[T]) extends Actor {
  val closeable = ActorCloseable(self)

  def receive = {
    case LiveProcessingStarted =>
      context watch sender
      observer.onLiveProcessingStart(closeable)

    case Failure(error) =>
      context watch sender
      observer.onError(error)

    case tag(x) =>
      context watch sender
      observer.onEvent(x, closeable)
  }

  override def postStop() = observer.onClose()
}
