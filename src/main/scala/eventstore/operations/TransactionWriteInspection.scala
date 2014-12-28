package eventstore
package operations

import OperationError._
import Inspection.Decision._

private[eventstore] case class TransactionWriteInspection(out: TransactionWrite)
    extends ErrorInspection[TransactionWriteCompleted, OperationError] {

  def decision(error: OperationError) = {
    error match {
      case PrepareTimeout       => Retry
      case CommitTimeout        => Retry
      case ForwardTimeout       => Retry
      case WrongExpectedVersion => Unexpected
      case StreamDeleted        => Unexpected
      case InvalidTransaction   => Unexpected
      case AccessDenied         => Fail(new AccessDeniedException(s"Write access denied"))
    }
  }
}