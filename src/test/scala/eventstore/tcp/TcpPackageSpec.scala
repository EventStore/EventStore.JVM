package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit

/**
 * @author Yaroslav Klymko
 */
class TcpPackageSpec extends SpecificationWithJUnit {
  "TcpPackage" should {
    "serialize/deserialize" in {
      for {
        correlationId <- List(newUuid, newUuid)
        msg <- List[InOut](HeartbeatRequestCommand, HeartbeatResponseCommand, Ping, Pong)
        authData <- None :: (for {
          login <- List("login1", "login2")
          password <- List("password1", "password2")
        } yield Some(AuthData(login, password)))
      } yield {
        val pack = TcpPackage(correlationId, msg, authData)
        TcpPackage.deserialize(pack.serialize) mustEqual pack
      }
    }
  }
}

