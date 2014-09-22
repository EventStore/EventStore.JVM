package eventstore

import java.io.Closeable

trait SubscriptionObserver[T] {
  /**
   * The action liveProcessingStarted is called when the subscription switches from
   * the reading phase to the live subscription phase.
   *
   * @param subscription A `Closeable` representing subscription which can be closed.
   */
  def onLiveProcessingStart(subscription: Closeable)

  /**
   * Method invoked when a new event is received over the subscription
   *
   * @param event A new event pushed to the subscription
   * @param subscription A `Closeable` representing subscription which can be closed.
   */
  def onEvent(event: T, subscription: Closeable)

  /**
   * Method invoked if the subscription is dropped due to some error
   *
   * @param e An error causes subscription termination
   */
  def onError(e: Throwable)

  /**
   * Method invoked if the subscription is dropped
   */
  def onClose()
}