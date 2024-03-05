package eventstore
package core
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] final case class TransactionStartInspection(out: TransactionStart)
    extends ErrorInspection[TransactionStartCompleted, OperationError] {

  def streamId        = out.streamId
  def expectedVersion = out.expectedVersion

  def decision(error: OperationError) = {
    error match {
      case PrepareTimeout       => Retry
      case CommitTimeout        => Retry
      case ForwardTimeout       => Retry
      case WrongExpectedVersion => Fail(wrongExpectedVersion)
      case StreamDeleted        => Fail(streamDeletedException)
      case InvalidTransaction   => Fail(InvalidTransactionException)
      case AccessDenied         => Fail(AccessDeniedException(s"Write access denied for $streamId"))
      case Unrecognized         => Fail(UnrecognizedException)
    }
  }

  def wrongExpectedVersion = {
    val msg = s"Transaction start failed due to WrongExpectedVersion: $streamId, $expectedVersion"
    new WrongExpectedVersionException(msg)
  }

  def streamDeletedException = {
    new StreamDeletedException(s"Transaction start failed due to $streamId has been deleted")
  }
}