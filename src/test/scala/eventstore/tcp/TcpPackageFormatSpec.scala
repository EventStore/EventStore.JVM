package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import eventstore.util.{BytesWriter, BytesReader}
import EventStoreFormats._

/**
 * @author Yaroslav Klymko
 */
class TcpPackageFormatSpec extends SpecificationWithJUnit {
  "TcpPackageFormat" should {
    "read/write" in {
      for {
        correlationId <- List(newUuid, newUuid)
        msg <- List[InOut](HeartbeatRequestCommand, HeartbeatResponseCommand, Ping, Pong)
      } yield {
        val expected = TcpPackageOut(correlationId, msg)
        val bs = BytesWriter.toByteString(expected)
        val actual = BytesReader.read[TcpPackageIn](bs)
        actual.correlationId mustEqual expected.correlationId
        actual.message mustEqual expected.message
      }
    }
  }
}

