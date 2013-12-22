package eventstore.util

import akka.actor.Cancellable

object CancellableAdapter {
  def apply(cancellables: Cancellable*): Cancellable = new Cancellable {
    require(cancellables.nonEmpty, "cancellables are empty")

    def cancel() = cancellables.map(_.cancel()).min

    def isCancelled = cancellables.forall(_.isCancelled)
  }
}
