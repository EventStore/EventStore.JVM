package eventstore
package core
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] final case class TransactionCommitInspection(out: TransactionCommit)
    extends ErrorInspection[TransactionCommitCompleted, OperationError] {

  def transactionId = out.transactionId

  def decision(error: OperationError) = {
    error match {
      case PrepareTimeout       => Retry
      case CommitTimeout        => Retry
      case ForwardTimeout       => Retry
      case WrongExpectedVersion => Fail(wrongExpectedVersionException)
      case StreamDeleted        => Fail(streamDeletedException)
      case InvalidTransaction   => Fail(InvalidTransactionException)
      case AccessDenied         => Fail(AccessDeniedException(s"Write access denied"))
      case Unrecognized         => Fail(UnrecognizedException)
    }
  }

  def wrongExpectedVersionException = {
    val msg = s"Transaction commit failed due to WrongExpectedVersion, transactionId: $transactionId"
    new WrongExpectedVersionException(msg)
  }

  def streamDeletedException = {
    val msg = s"Transaction commit due to stream has been deleted, transactionId: $transactionId"
    new StreamDeletedException(msg)
  }
}