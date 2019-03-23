package eventstore
package core
package util

import scala.concurrent.duration.{Duration, FiniteDuration}

object DelayedRetry {
  def opt(left: Int, delay: FiniteDuration, maxDelay: FiniteDuration): Option[DelayedRetry] = {
    if (left == 0) None
    else Some(DelayedRetry(left, Duration.fromNanos(delay.toNanos), maxDelay))
  }
}

final case class DelayedRetry private[DelayedRetry](left: Int, delay: FiniteDuration, delayMax: FiniteDuration) {
  require(delay > Duration.Zero, "delay must be > 0")
  require(delayMax > Duration.Zero, "delayMax must be > 0")
  require(left != 0, "left must be != 0")

  def next: Option[DelayedRetry] = {
    val newLeft = (left - 1) max -1
    if (newLeft == 0) None
    else Some {
      val newDelay = (delay * 2) min delayMax
      copy(left = newLeft, delay = Duration.fromNanos(newDelay.toNanos))
    }
  }
}