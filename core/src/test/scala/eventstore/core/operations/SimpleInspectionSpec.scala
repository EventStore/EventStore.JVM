package eventstore
package core
package operations

import scala.util.Success
import org.specs2.mutable.Specification
import Inspection.Decision._

class SimpleInspectionSpec extends Specification {
  "SimpleInspection" should {
    "handle provided value only" in {
      val in = ClientIdentified
      val inspection = new SimpleInspection(in).pf
      inspection(Success(in)) mustEqual Stop
    }
  }
}
