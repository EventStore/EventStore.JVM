package eventstore
package operations

import eventstore.ScavengeError.{ InProgress, Failed }
import eventstore.operations.Inspection.Decision.{ Fail, Stop }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class ScavengeDatabaseInspectionSpec extends Specification with Mockito {
  val inspection = ScavengeDatabaseInspection.pf

  "ScavengeDatabaseInspection" should {

    "handle ScavengeDatabaseCompleted" in {
      inspection(Success(mock[ScavengeDatabaseCompleted])) mustEqual Stop
    }

    "handle InProgress" in {
      inspection(Failure(InProgress)) mustEqual Fail(ScavengeInProgressException)
    }

    "handle Failed" in {
      inspection(Failure(Failed(None))) must beLike {
        case Fail(_: ScavengeFailedException) => ok
      }
    }
  }
}
