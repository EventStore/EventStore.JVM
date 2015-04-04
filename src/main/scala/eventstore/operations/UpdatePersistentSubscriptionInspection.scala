package eventstore
package operations

import eventstore.PersistentSubscription.{ Update, UpdateCompleted }
import eventstore.UpdatePersistentSubscriptionError._
import eventstore.operations.Inspection.Decision.Fail

private[eventstore] case class UpdatePersistentSubscriptionInspection(out: Update)
    extends ErrorInspection[UpdateCompleted.type, UpdatePersistentSubscriptionError] {

  def decision(error: UpdatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => new AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case Error(error) => new ServerErrorException(error.orNull)
    }
    Fail(result)
  }

  def streamId = out.streamId
}
