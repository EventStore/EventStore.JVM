package eventstore
package operations

import eventstore.OperationError._
import eventstore.operations.Inspection.Decision.{ Unexpected, Retry, Stop, Fail }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class TransactionWriteInspectionSpec extends Specification with Mockito {
  val inspection = TransactionWriteInspection(mock[TransactionWrite]).pf

  "TransactionStartInspection" should {

    "handle TransactionStartCompleted" in {
      inspection(Success(mock[TransactionWriteCompleted])) mustEqual Stop
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
