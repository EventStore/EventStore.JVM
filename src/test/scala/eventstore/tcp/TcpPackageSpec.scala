package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import eventstore.util.{BytesWriter, BytesReader}
import EventStoreFormats._

/**
 * @author Yaroslav Klymko
 */
class TcpPackageSpec extends SpecificationWithJUnit {
  "TcpPackage" should {
    "read/write" in {
      for {
        correlationId <- List(newUuid, newUuid)
        msg <- List[InOut](HeartbeatRequestCommand, HeartbeatResponseCommand, Ping, Pong)
        authData <- None :: (for {
          login <- List("login1", "login2")
          password <- List("password1", "password2")
        } yield Some(AuthData(login, password)))
      } yield {
        val expected = TcpPackageOut(correlationId, msg, authData)
        val bs = BytesWriter.toByteString(expected)
        val actual = BytesReader.read[TcpPackageIn](bs)
        actual.correlationId mustEqual expected.correlationId
        actual.message mustEqual expected.message
        actual.auth mustEqual expected.auth
      }
    }
  }
}

