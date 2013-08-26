package eventstore

import org.specs2.mutable.Specification

/**
 * @author Yaroslav Klymko
 */
class UserCredentialsSpec extends Specification {
  "UserCredentials" should {
    "not show password in toString" in {
      val password = "myPassword"
      UserCredentials(login = "myLogin", password = password).toString must not(contain(password))
    }
  }
}
