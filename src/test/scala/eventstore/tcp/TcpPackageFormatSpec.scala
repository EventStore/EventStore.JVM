package eventstore
package tcp

import EventStoreFormats._
import util.{ BytesWriter, BytesReader }
import org.specs2.mutable.Specification
import scala.util.Success

class TcpPackageFormatSpec extends Specification {
  "TcpPackageFormat" should {
    "read/write" in foreach(List(randomUuid, randomUuid)) {
      correlationId =>
        foreach(List[InOut](HeartbeatRequest, HeartbeatResponse, Ping, Pong)) {
          msg =>
            val expected = TcpPackageOut(msg, correlationId)
            val bs = BytesWriter[TcpPackageOut].toByteString(expected)
            val actual = BytesReader[TcpPackageIn].read(bs)
            actual.correlationId mustEqual expected.correlationId
            actual.message mustEqual Success(expected.message)
        }
    }
  }
}

