package eventstore
package core
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import OperationError._
import Inspection.Decision.{Unexpected, Retry, Stop, Fail}
import TestData._

class TransactionWriteInspectionSpec extends Specification {
  val inspection = TransactionWriteInspection(transactionWrite).pf

  "TransactionStartInspection" should {

    "handle TransactionStartCompleted" in {
      inspection(Success(transactionWriteCompleted)) mustEqual Stop
    }

    "handle CommitTimeout" in {
      inspection(Failure(CommitTimeout)) mustEqual Retry
    }

    "handle ForwardTimeout" in {
      inspection(Failure(ForwardTimeout)) mustEqual Retry
    }

    "handle PrepareTimeout" in {
      inspection(Failure(PrepareTimeout)) mustEqual Retry
    }

    "handle WrongExpectedVersion" in {
      inspection(Failure(WrongExpectedVersion)) mustEqual Unexpected
    }

    "handle StreamDeleted" in {
      inspection(Failure(StreamDeleted)) mustEqual Unexpected
    }

    "handle InvalidTransaction" in {
      inspection(Failure(InvalidTransaction)) mustEqual Unexpected
    }

    "handle AccessDenied" in {
      inspection(Failure(AccessDenied)) must beLike {
        case Fail(_: AccessDeniedException) => ok
      }
    }
  }
}
