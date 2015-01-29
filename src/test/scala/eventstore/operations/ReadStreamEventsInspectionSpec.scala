package eventstore
package operations

import eventstore.ReadStreamEventsError._
import eventstore.operations.Inspection.Decision.{ Stop, Fail }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class ReadStreamEventsInspectionSpec extends Specification with Mockito {
  val inspection = ReadStreamEventsInspection(mock[ReadStreamEvents]).pf

  "ReadStreamEventsInspection" should {

    "handle ReadStreamEventsCompleted" in {
      inspection(Success(mock[ReadStreamEventsCompleted])) mustEqual Stop
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
