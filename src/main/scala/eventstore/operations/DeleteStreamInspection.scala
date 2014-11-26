package eventstore
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] class DeleteStreamInspection(out: DeleteStream)
    extends AbstractInspection[DeleteStreamCompleted, OperationError] {

  def decision(error: OperationError) = {
    error match {
      case PrepareTimeout       => Retry
      case CommitTimeout        => Retry
      case ForwardTimeout       => Retry
      case WrongExpectedVersion => Fail(wrongExpectedVersionException)
      case StreamDeleted        => Fail(streamDeletedException)
      case InvalidTransaction   => Fail(InvalidTransactionException)
      case AccessDenied         => Fail(new AccessDeniedException(s"Write access denied for $streamId"))
    }
  }

  def streamId = out.streamId

  def expectedVersion = out.expectedVersion

  def wrongExpectedVersionException = {
    WrongExpectedVersionException(s"Delete stream failed due to WrongExpectedVersion: $streamId, $expectedVersion")
  }

  def streamDeletedException = {
    new StreamDeletedException(s"Delete stream failed due to $streamId has been deleted")
  }
}