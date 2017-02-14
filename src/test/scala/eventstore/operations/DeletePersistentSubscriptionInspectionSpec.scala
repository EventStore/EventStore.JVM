package eventstore
package operations

import eventstore.DeletePersistentSubscriptionError._
import eventstore.PersistentSubscription.{ Delete, DeleteCompleted }
import eventstore.operations.Inspection.Decision.{ Fail, Stop }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.{ Failure, Success }

class DeletePersistentSubscriptionInspectionSpec extends Specification with Mockito {
  val inspection = DeletePersistentSubscriptionInspection(mock[Delete]).pf

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