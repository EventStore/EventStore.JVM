package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import EventStoreFormats.AuthDataFormat

/**
 * @author Yaroslav Klymko
 */
class AuthDataFormatSpec extends SpecificationWithJUnit {
  "AuthDataFormat" should {
    "read/write" in {
      for {
        login <- List("login1", "login2")
        password <- List("password1", "password2")
      } yield {
        val expected = AuthData(login, password)
        val bs = AuthDataFormat.toByteString(expected)
        val actual = AuthDataFormat.read(bs)
        actual mustEqual expected
      }
    }
  }
}
