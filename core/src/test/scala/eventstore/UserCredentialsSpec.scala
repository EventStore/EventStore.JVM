package eventstore

import org.specs2.mutable.Specification

class UserCredentialsSpec extends Specification {

  "UserCredentials" should {
    
    "not show password in toString" in {
      val password = "myPassword"
      UserCredentials(login = "myLogin", password = password).toString must not(contain(password))
    }

    "require not-null and non-empty values" in {
      UserCredentials(login = null, password = "password") must throwAn[IllegalArgumentException]
      UserCredentials(login = "", password = "password") must throwAn[IllegalArgumentException]
      UserCredentials(login = "login", password = null) must throwAn[IllegalArgumentException]
      UserCredentials(login = "login", password = "") must throwAn[IllegalArgumentException]
    }
  }
}
