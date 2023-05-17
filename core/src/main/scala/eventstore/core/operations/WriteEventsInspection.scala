package eventstore
package core
package operations

import eventstore.core.OperationError._
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
    case AccessDenied         => Fail(accessDenied)
    case Unrecognized         => Fail(UnrecognizedException)
  }

  def accessDenied: AccessDeniedException = AccessDeniedException(
    s"Write failed due to access being denied for $streamId"
  )

  def wrongExpectedVersion: WrongExpectedVersionException =  WrongExpectedVersionException(
    s"Write failed due to wrong expected version: $streamId, $expectedVersion"
  )

  def streamDeleted: StreamDeletedException = StreamDeletedException(
    s"Write failed due to $streamId has been deleted"
  )
}