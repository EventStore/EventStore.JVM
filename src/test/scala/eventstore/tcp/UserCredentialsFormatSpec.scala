package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import EventStoreFormats._
import util.{BytesReader, BytesWriter}

/**
 * @author Yaroslav Klymko
 */
class UserCredentialsFormatSpec extends SpecificationWithJUnit {
  "UserCredentialsFormat" should {
    "read/write" in {
      for {
        login <- List("login1", "login2")
        password <- List("password1", "password2")
      } yield {
        val expected = UserCredentials(login, password)
        val bs = BytesWriter.toByteString(expected)
        val actual = BytesReader.read[UserCredentials](bs)
        actual mustEqual expected
      }
    }

    "throw exception if login is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = long, password = "password")
      BytesWriter.toByteString(userCredentials) must throwAn[IllegalArgumentException]
    }

    "throw exception if password is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = "login", password = long)
      BytesWriter.toByteString(userCredentials) must throwAn[IllegalArgumentException]
    }
  }
}
