package eventstore
package operations

import CreatePersistentSubscriptionError._
import PersistentSubscription.{Create, CreateCompleted}
import Inspection.Decision.Fail

private[eventstore] final case class CreatePersistentSubscriptionInspection(out: Create)
    extends ErrorInspection[CreateCompleted.type, CreatePersistentSubscriptionError] {

  def decision(error: CreatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied  => new AccessDeniedException(s"Read access denied for $streamId")
      case AlreadyExists => new InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId already exists")
      case Error(msg)    => new ServerErrorException(msg.orNull)
    }
    Fail(result)
  }

  def streamId = out.streamId
}