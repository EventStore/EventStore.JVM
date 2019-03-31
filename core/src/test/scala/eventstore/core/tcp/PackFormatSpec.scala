package eventstore
package core
package tcp

import scala.util.Success
import org.specs2.mutable.Specification
import syntax._
import util.uuid.randomUuid
import EventStoreFormats._

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

