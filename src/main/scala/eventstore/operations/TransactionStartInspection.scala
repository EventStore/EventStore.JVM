package eventstore
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] case class TransactionStartInspection(out: TransactionStart)
    extends ErrorInspection[TransactionStartCompleted, OperationError] {

  def decision(error: OperationError) = {
    error match {
      case PrepareTimeout       => Retry
      case CommitTimeout        => Retry
      case ForwardTimeout       => Retry
      case WrongExpectedVersion => Fail(wrongExpectedVersion)
      case StreamDeleted        => Fail(streamDeletedException)
      case InvalidTransaction   => Fail(InvalidTransactionException)
      case AccessDenied         => Fail(new AccessDeniedException(s"Write access denied for $streamId"))
    }
  }

  def streamId = out.streamId

  def expectedVersion = out.expectedVersion

  def wrongExpectedVersion = {
    val msg = s"Transaction start failed due to WrongExpectedVersion: $streamId, $expectedVersion"
    new WrongExpectedVersionException(msg)
  }

  def streamDeletedException = {
    new StreamDeletedException(s"Transaction start failed due to $streamId has been deleted")
  }
}