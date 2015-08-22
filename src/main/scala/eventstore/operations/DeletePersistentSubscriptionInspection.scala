package eventstore
package operations

import eventstore.DeletePersistentSubscriptionError.{ Error, DoesNotExist, AccessDenied }
import eventstore.PersistentSubscription.{ Delete, DeleteCompleted }
import eventstore.operations.Inspection.Decision.Fail

private[eventstore] case class DeletePersistentSubscriptionInspection(out: Delete)
    extends ErrorInspection[DeleteCompleted.type, DeletePersistentSubscriptionError] {

  def decision(error: DeletePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => new AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case Error(error) => new ServerErrorException(error.orNull)
    }
    Fail(result)
  }

  def streamId = out.streamId
}