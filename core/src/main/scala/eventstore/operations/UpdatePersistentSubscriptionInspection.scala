package eventstore
package operations

import PersistentSubscription.{ Update, UpdateCompleted }
import UpdatePersistentSubscriptionError._
import Inspection.Decision.Fail

private[eventstore] case class UpdatePersistentSubscriptionInspection(out: Update)
    extends ErrorInspection[UpdateCompleted.type, UpdatePersistentSubscriptionError] {

  def streamId = out.streamId

  def decision(error: UpdatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => new AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case Error(msg)   => new ServerErrorException(msg.orNull)
    }
    Fail(result)
  }
}