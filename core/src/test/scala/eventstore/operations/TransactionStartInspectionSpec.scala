package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import OperationError._
import Inspection.Decision.{Retry, Stop, Fail}

class TransactionStartInspectionSpec extends Specification with Mockito {
  val inspection = TransactionStartInspection(mock[TransactionStart]).pf

  "TransactionStartInspection" should {

    "handle TransactionStartCompleted" in {
      inspection(Success(mock[TransactionStartCompleted])) mustEqual Stop
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
      inspection(Failure(WrongExpectedVersion)) must beLike {
        case Fail(_: WrongExpectedVersionException) => ok
      }
    }

    "handle StreamDeleted" in {
      inspection(Failure(StreamDeleted)) must beLike {
        case Fail(_: StreamDeletedException) => ok
      }
    }

    "handle InvalidTransaction" in {
      inspection(Failure(InvalidTransaction)) mustEqual Fail(InvalidTransactionException)
    }

    "handle AccessDenied" in {
      inspection(Failure(AccessDenied)) must beLike {
        case Fail(_: AccessDeniedException) => ok
      }
    }
  }
}
