package eventstore
package operations

import eventstore.CreatePersistentSubscriptionError._
import eventstore.PersistentSubscription.{ Create, CreateCompleted }
import eventstore.operations.Inspection.Decision.Fail

private[eventstore] case class CreatePersistentSubscriptionInspection(out: Create)
    extends ErrorInspection[CreateCompleted.type, CreatePersistentSubscriptionError] {

  def decision(error: CreatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied  => new AccessDeniedException(s"Read access denied for $streamId")
      case AlreadyExists => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId already exists")
      case Error(error)  => new ServerErrorException(error.orNull)
    }
    Fail(result)
  }

  def streamId = out.streamId
}