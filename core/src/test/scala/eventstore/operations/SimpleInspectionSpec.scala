package eventstore
package operations

import scala.util.Success
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import Inspection.Decision._

class SimpleInspectionSpec extends Specification with Mockito {
  "SimpleInspection" should {
    "handle provided value only" in {
      val in = mock[In]
      val inspection = new SimpleInspection(in).pf
      inspection(Success(in)) mustEqual Stop
    }
  }
}
