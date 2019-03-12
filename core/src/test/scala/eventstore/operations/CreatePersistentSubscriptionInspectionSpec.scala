package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import CreatePersistentSubscriptionError._
import PersistentSubscription.{Create, CreateCompleted}
import Inspection.Decision._

class CreatePersistentSubscriptionInspectionSpec extends Specification with Mockito {
  val inspection = CreatePersistentSubscriptionInspection(mock[Create]).pf

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