package eventstore
package core
package operations

import DeletePersistentSubscriptionError.{Error, DoesNotExist, AccessDenied, Unrecognized}
import PersistentSubscription.{Delete, DeleteCompleted}
import Inspection.Decision.Fail

private[eventstore] final case class DeletePersistentSubscriptionInspection(out: Delete)
    extends ErrorInspection[DeleteCompleted.type, DeletePersistentSubscriptionError] {

  def decision(error: DeletePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case Unrecognized => UnrecognizedException
      case e: Error     => ServerErrorException(e.message.getOrElse(e.toString))
    }
    Fail(result)
  }

  def streamId = out.streamId
}