package eventstore
package core
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import ReadAllEventsError._
import Inspection.Decision.{Stop, Fail}
import TestData._

class ReadAllEventsInspectionSpec extends Specification {
  val inspection = ReadAllEventsInspection(readAllEvents).pf

  "ReadAllEventsInspection" should {

    "handle ReadAllEventsCompleted" in {
      inspection(Success(readAllEventsCompleted)) mustEqual Stop
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
