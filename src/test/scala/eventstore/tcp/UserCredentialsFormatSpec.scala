package eventstore
package tcp

import org.specs2.mutable.Specification
import EventStoreFormats._
import util._

class UserCredentialsFormatSpec extends Specification {
  "UserCredentialsFormat" should {
    "read/write" in foreach(List("login1", "login2", List.fill(255)("x").mkString)) {
      login =>
        foreach(List("password1", "password2", List.fill(255)("x").mkString)) {
          password =>
            val expected = UserCredentials(login, password)
            val bs = BytesWriter[UserCredentials].write(expected)
            val actual = BytesReader[UserCredentials].read(bs).unsafe.value
            actual mustEqual expected
        }
    }

    "throw exception if login is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = long, password = "password")
      BytesWriter[UserCredentials].write(userCredentials) must throwAn[IllegalArgumentException]
    }

    "throw exception if password is too long" in {
      val long = List.fill(500)("x").mkString
      val userCredentials = UserCredentials(login = "login", password = long)
      BytesWriter[UserCredentials].write(userCredentials) must throwAn[IllegalArgumentException]
    }
  }
}
