package eventstore
package operations

import scala.util.{ Failure, Success }
import org.specs2.mutable.Specification
import ScavengeError.{InProgress, Unauthorized}
import Inspection.Decision.{Fail, Stop}
import TestData._

class ScavengeDatabaseInspectionSpec extends Specification {
  val inspection = ScavengeDatabaseInspection.pf

  "ScavengeDatabaseInspection" should {

    "handle ScavengeDatabaseCompleted" in {
      inspection(Success(scavengeDatabaseResponse)) mustEqual Stop
    }

    "handle InProgress" in {
      inspection(Failure(InProgress)) mustEqual Fail(ScavengeInProgressException)
    }

    "handle Failed" in {
      inspection(Failure(Unauthorized)) mustEqual Fail(ScavengeUnauthorizedException)
    }

  }
}
