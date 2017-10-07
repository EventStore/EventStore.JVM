package eventstore
package operations

import org.specs2.mutable.Specification
import eventstore.operations.Inspection.Decision.{ Stop, Fail }
import scala.util.{ Failure, Success }

class IdentifyClientInspectionSpec extends Specification {

  val inspection = IdentifyClientInspection.pf

  "IdentifyClientInspection" should {

    "handle ClientIdentified" in {
      inspection(Success(ClientIdentified)) mustEqual Stop
    }

  }

}
