package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import UpdatePersistentSubscriptionError._
import PersistentSubscription.{Update, UpdateCompleted}
import Inspection.Decision._

class UpdatePersistentSubscriptionInspectionSpec extends Specification with Mockito {
  val inspection = UpdatePersistentSubscriptionInspection(mock[Update]).pf

  "UpdatePersistentSubscriptionInspection" should {

    "handle UpdateCompleted" in {
      inspection(Success(UpdateCompleted)) mustEqual Stop
    }

    "handle AccessDenied" in {
      inspection(Failure(AccessDenied)) must beLike {
        case Fail(_: AccessDeniedException) => ok
      }
    }

    "handle Error" in {
      inspection(Failure(Error(None))) must beLike {
        case Fail(_: ServerErrorException) => ok
      }
    }

    "handle DoesNotExist" in {
      inspection(Failure(DoesNotExist)) must beLike {
        case Fail(_: InvalidOperationException) => ok
      }
    }
  }
}