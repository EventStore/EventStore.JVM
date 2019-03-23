package eventstore
package core
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import UpdatePersistentSubscriptionError._
import PersistentSubscription.UpdateCompleted
import Inspection.Decision._
import TestData._

class UpdatePersistentSubscriptionInspectionSpec extends Specification {
  val inspection = UpdatePersistentSubscriptionInspection(psUpdate).pf

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