package eventstore
package core
package operations

import DeletePersistentSubscriptionError.{Error, DoesNotExist, AccessDenied}
import PersistentSubscription.{Delete, DeleteCompleted}
import Inspection.Decision.Fail

private[eventstore] final case class DeletePersistentSubscriptionInspection(out: Delete)
    extends ErrorInspection[DeleteCompleted.type, DeletePersistentSubscriptionError] {

  def decision(error: DeletePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => new AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case Error(msg)   => new ServerErrorException(msg.orNull)
    }
    Fail(result)
  }

  def streamId = out.streamId
}