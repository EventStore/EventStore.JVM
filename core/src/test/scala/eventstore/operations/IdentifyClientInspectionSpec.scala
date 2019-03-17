package eventstore
package operations

import scala.util.Success
import org.specs2.mutable.Specification
import Inspection.Decision.Stop

class IdentifyClientInspectionSpec extends Specification {

  val inspection = IdentifyClientInspection.pf

  "IdentifyClientInspection" should {

    "handle ClientIdentified" in {
      inspection(Success(ClientIdentified)) mustEqual Stop
    }

  }

}
