package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import ReadEventError._
import Inspection.Decision.{Stop, Fail}
import TestData._

class ReadEventInspectionSpec extends Specification {
  val inspection = ReadEventInspection(readEvent).pf

  "ReadEventInspection" should {

    "handle ReadEventCompleted" in {
      inspection(Success(readEventCompleted)) mustEqual Stop
    }

    "handle StreamNotFound" in {
      inspection(Failure(StreamNotFound)) must beLike {
        case Fail(_: StreamNotFoundException) => ok
      }
    }

    "handle StreamDeleted" in {
      inspection(Failure(StreamDeleted)) must beLike {
        case Fail(_: StreamDeletedException) => ok
      }
    }

    "handle EventNotFound" in {
      inspection(Failure(EventNotFound)) must beLike {
        case Fail(_: EventNotFoundException) => ok
      }
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
