package eventstore
package core
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import DeletePersistentSubscriptionError._
import PersistentSubscription.DeleteCompleted
import Inspection.Decision._
import TestData._

class DeletePersistentSubscriptionInspectionSpec extends Specification {
  val inspection = DeletePersistentSubscriptionInspection(psDelete).pf

  "DeletePersistentSubscriptionInspection" should {

    "handle DeleteCompleted" in {
      inspection(Success(DeleteCompleted)) mustEqual Stop
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