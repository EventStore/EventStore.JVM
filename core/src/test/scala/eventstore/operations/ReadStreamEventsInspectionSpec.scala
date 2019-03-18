package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import ReadStreamEventsError._
import Inspection.Decision.{Stop, Fail}
import TestData._

class ReadStreamEventsInspectionSpec extends Specification {

  val inspection = ReadStreamEventsInspection(readStreamEvents).pf

  "ReadStreamEventsInspection" should {

    "handle ReadStreamEventsCompleted" in {
      inspection(Success(readStreamEventsCompleted)) mustEqual Stop
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
