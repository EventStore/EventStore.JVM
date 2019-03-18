package eventstore
package operations

import eventstore.OperationError._
import Inspection.Decision._

private[eventstore] final case class WriteEventsInspection(out: WriteEvents)
    extends ErrorInspection[WriteEventsCompleted, OperationError] {

  def streamId = out.streamId
  def expectedVersion = out.expectedVersion

  def decision(error: OperationError) = error match {
    case PrepareTimeout       => Retry
    case CommitTimeout        => Retry
    case ForwardTimeout       => Retry
    case WrongExpectedVersion => Fail(wrongExpectedVersion)
    case StreamDeleted        => Fail(streamDeleted)
    case InvalidTransaction   => Fail(InvalidTransactionException)
    case AccessDenied         => Fail(new AccessDeniedException(s"Write access denied for $streamId"))
  }

  def wrongExpectedVersion = {
    val msg = s"Write failed due to WrongExpectedVersion: $streamId, $expectedVersion"
    new WrongExpectedVersionException(msg)
  }

  def streamDeleted = new StreamDeletedException(s"Write failed due to $streamId has been deleted")
}