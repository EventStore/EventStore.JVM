package eventstore
package akka

import scala.reflect.ClassTag
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{Actor, Props}

object SubscriptionObserverActor {

  def props[T](observer: SubscriptionObserver[T])(implicit tag: ClassTag[T]): Props =
    Props(new SubscriptionObserverActor[T](observer, tag))

  /**
   * Java API
   */
  def getProps[T](observer: SubscriptionObserver[T], clazz: Class[T]): Props = props(observer)(ClassTag(clazz))
}

private[eventstore] class SubscriptionObserverActor[T](observer: SubscriptionObserver[T], tag: ClassTag[T])
  extends Actor {

  val closeable = ActorCloseable(self)

  def receive = {
    case LiveProcessingStarted =>
      context watch sender()
      observer.onLiveProcessingStart(closeable)

    case Failure(error) =>
      context watch sender()
      observer.onError(error)

    case tag(x) =>
      context watch sender()
      observer.onEvent(x, closeable)
  }

  override def postStop() = observer.onClose()
}
