package eventstore
package tcp

import EventStoreFormats._
import util.{ BytesWriter, BytesReader }
import org.specs2.mutable.Specification
import scala.util.Success

class TcpPackageFormatSpec extends Specification {
  "TcpPackageFormat" should {
    "read/write" in {
      for {
        correlationId <- List(randomUuid, randomUuid)
        msg <- List[InOut](HeartbeatRequest, HeartbeatResponse, Ping, Pong)
      } yield {
        val expected = TcpPackageOut(msg, correlationId)
        val bs = BytesWriter[TcpPackageOut].toByteString(expected)
        val actual = BytesReader[TcpPackageIn].read(bs)
        actual.correlationId mustEqual expected.correlationId
        actual.message mustEqual Success(expected.message)
      }
    }
  }
}

