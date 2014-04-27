package eventstore
package tcp

import org.specs2.mutable.Specification
import EventStoreFormats._
import util.{ BytesReader, BytesWriter }

class UserCredentialsFormatSpec extends Specification {
  "UserCredentialsFormat" should {
    "read/write" in foreach(List("login1", "login2")) {
      login =>
        foreach(List("password1", "password2")) {
          password =>
            val expected = UserCredentials(login, password)
            val bs = BytesWriter[UserCredentials].toByteString(expected)
            val actual = BytesReader[UserCredentials].read(bs)
            actual mustEqual expected
        }
    }

    "throw exception if login is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = long, password = "password")
      BytesWriter[UserCredentials].toByteString(userCredentials) must throwAn[IllegalArgumentException]
    }

    "throw exception if password is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = "login", password = long)
      BytesWriter[UserCredentials].toByteString(userCredentials) must throwAn[IllegalArgumentException]
    }
  }
}
