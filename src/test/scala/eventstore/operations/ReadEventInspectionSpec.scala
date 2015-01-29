package eventstore
package operations

import eventstore.ReadEventError._
import eventstore.operations.Inspection.Decision.{ Stop, Fail }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class ReadEventInspectionSpec extends Specification with Mockito {
  val inspection = ReadEventInspection(ReadEvent(EventStream.Id("test"))).pf

  "ReadEventInspection" should {

    "handle ReadEventCompleted" in {
      inspection(Success(ReadEventCompleted(mock[Event]))) mustEqual Stop
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
