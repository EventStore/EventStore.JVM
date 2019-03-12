package eventstore
package tcp

import EventStoreFormats._
import util.{BytesWriter, BytesReader}
import org.specs2.mutable.Specification
import scala.util.Success

class PackFormatSpec extends Specification {
  "PackFormatFormat" should {
    "read/write" in foreach(List(randomUuid, randomUuid)) {
      correlationId =>
        foreach(List[InOut](HeartbeatRequest, HeartbeatResponse, Ping, Pong)) {
          msg =>
            val expected = PackOut(msg, correlationId)
            val bs = BytesWriter[PackOut].write(expected)
            val actual = BytesReader[PackIn].read(bs).unsafe.value
            actual.correlationId mustEqual expected.correlationId
            actual.message mustEqual Success(expected.message)
        }
    }
  }
}

