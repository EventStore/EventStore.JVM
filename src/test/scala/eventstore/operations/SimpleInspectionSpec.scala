package eventstore
package operations

import eventstore.operations.Inspection.Decision.Stop
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.util.Success

class SimpleInspectionSpec extends Specification with Mockito {
  "SimpleInspection" should {
    "handle provided value only" in {
      val in = mock[In]
      val inspection = new SimpleInspection(in).pf
      inspection(Success(in)) mustEqual Stop
    }
  }
}
