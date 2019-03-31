package eventstore
package core
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import CreatePersistentSubscriptionError._
import PersistentSubscription.CreateCompleted
import Inspection.Decision._
import TestData._

class CreatePersistentSubscriptionInspectionSpec extends Specification {
  val inspection = CreatePersistentSubscriptionInspection(psCreate).pf

  "CreatePersistentSubscriptionInspection" should {

    "handle CreateCompleted" in {
      inspection(Success(CreateCompleted)) mustEqual Stop
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

    "handle AlreadyExists" in {
      inspection(Failure(AlreadyExists)) must beLike {
        case Fail(_: InvalidOperationException) => ok
      }
    }
  }
}