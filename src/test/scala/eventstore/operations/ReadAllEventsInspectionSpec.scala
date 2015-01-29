package eventstore
package operations

import eventstore.ReadAllEventsError._
import eventstore.operations.Inspection.Decision.{ Stop, Fail }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class ReadAllEventsInspectionSpec extends Specification with Mockito {
  val inspection = ReadAllEventsInspection(mock[ReadAllEvents]).pf

  "ReadAllEventsInspection" should {

    "handle ReadAllEventsCompleted" in {
      inspection(Success(mock[ReadAllEventsCompleted])) mustEqual Stop
    }

    "handle Error" in {
      inspection(Failure(Error(None))) must beLike {
        case Fail(_: ServerErrorException) => ok
      }
    }

    "handle AccessDenied" in {
      inspection(Failure(AccessDenied)) must beLike {
        case Fail(_: AccessDeniedException) => ok
      }
    }
  }
}
