package eventstore
package operations

import eventstore.ScavengeError.{ InProgress, Unauthorized }
import eventstore.operations.Inspection.Decision.{ Fail, Stop }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.{ Failure, Success }

class ScavengeDatabaseInspectionSpec extends Specification with Mockito {
  val inspection = ScavengeDatabaseInspection.pf

  "ScavengeDatabaseInspection" should {

    "handle ScavengeDatabaseCompleted" in {
      inspection(Success(mock[ScavengeDatabaseResponse])) mustEqual Stop
    }

    "handle InProgress" in {
      inspection(Failure(InProgress)) mustEqual Fail(ScavengeInProgressException)
    }

    "handle Failed" in {
      inspection(Failure(Unauthorized)) mustEqual Fail(ScavengeUnauthorizedException)
    }

  }
}
