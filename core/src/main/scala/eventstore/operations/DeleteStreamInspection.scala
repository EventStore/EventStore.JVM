package eventstore
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] final case class DeleteStreamInspection(out: DeleteStream)
    extends ErrorInspection[DeleteStreamCompleted, OperationError] {

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
    val msg = s"Delete stream failed due to WrongExpectedVersion: $streamId, $expectedVersion"
    new WrongExpectedVersionException(msg)
  }

  def streamDeletedException = {
    new StreamDeletedException(s"Delete stream failed due to $streamId has been deleted")
  }
}