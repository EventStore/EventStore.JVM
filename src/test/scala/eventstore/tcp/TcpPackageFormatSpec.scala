package eventstore
package tcp

import org.specs2.mutable.Specification
import eventstore.util.{ BytesWriter, BytesReader }
import EventStoreFormats._

/**
 * @author Yaroslav Klymko
 */
class TcpPackageFormatSpec extends Specification {
  "TcpPackageFormat" should {
    "read/write" in {
      for {
        correlationId <- List(newUuid, newUuid)
        msg <- List[InOut](HeartbeatRequest, HeartbeatResponse, Ping, Pong)
      } yield {
        val expected = TcpPackageOut(correlationId, msg)
        val bs = BytesWriter[TcpPackageOut].toByteString(expected)
        val actual = BytesReader[TcpPackageIn].read(bs)
        actual.correlationId mustEqual expected.correlationId
        actual.message mustEqual expected.message
      }
    }
  }
}

