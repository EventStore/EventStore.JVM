package eventstore
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] class WriteEventsInspection(out: WriteEvents)
    extends AbstractInspection[WriteEventsCompleted, OperationError] {

  def decision(error: OperationError) = error match {
    case PrepareTimeout       => Retry
    case CommitTimeout        => Retry
    case ForwardTimeout       => Retry
    case WrongExpectedVersion => Fail(wrongExpectedVersion)
    case StreamDeleted        => Fail(streamDeleted)
    case InvalidTransaction   => Fail(InvalidTransactionException)
    case AccessDenied         => Fail(new AccessDeniedException(s"Write access denied for $streamId"))
  }

  def streamId = out.streamId

  def expectedVersion = out.expectedVersion

  def wrongExpectedVersion = WrongExpectedVersionException(s"Write failed due to WrongExpectedVersion: $streamId, $expectedVersion")

  def streamDeleted = new StreamDeletedException(s"Write failed due to $streamId has been deleted")
}