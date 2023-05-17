package eventstore
package core
package operations

import PersistentSubscription.{ Update, UpdateCompleted }
import UpdatePersistentSubscriptionError._
import Inspection.Decision.Fail

private[eventstore] final case class UpdatePersistentSubscriptionInspection(out: Update)
    extends ErrorInspection[UpdateCompleted.type, UpdatePersistentSubscriptionError] {

  def streamId = out.streamId

  def decision(error: UpdatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied => AccessDeniedException(s"Write access denied for stream $streamId")
      case DoesNotExist => InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId does not exist")
      case e: Error     => ServerErrorException(e.message.getOrElse(e.toString))
      case Unrecognized => UnrecognizedException
    }
    Fail(result)
  }
}