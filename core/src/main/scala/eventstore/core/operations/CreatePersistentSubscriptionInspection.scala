package eventstore
package core
package operations

import CreatePersistentSubscriptionError._
import PersistentSubscription.{Create, CreateCompleted}
import Inspection.Decision.Fail

private[eventstore] final case class CreatePersistentSubscriptionInspection(out: Create)
    extends ErrorInspection[CreateCompleted.type, CreatePersistentSubscriptionError] {

  def decision(error: CreatePersistentSubscriptionError) = {
    val result = error match {
      case AccessDenied  => AccessDeniedException(s"Read access denied for $streamId")
      case AlreadyExists => InvalidOperationException(s"Subscription group ${out.groupName} on stream $streamId already exists")
      case Unrecognized  => UnrecognizedException
      case e: Error      => ServerErrorException(e.message.getOrElse(e.toString))
    }
    Fail(result)
  }

  def streamId = out.streamId
}