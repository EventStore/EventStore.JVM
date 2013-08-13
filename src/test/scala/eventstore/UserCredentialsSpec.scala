package eventstore

import org.specs2.mutable.SpecificationWithJUnit

/**
 * @author Yaroslav Klymko
 */
class UserCredentialsSpec extends SpecificationWithJUnit {
  "UserCredentials" should {
    "not show password in toString" in {
      val password = "myPassword"
      UserCredentials(login = "myLogin", password = password).toString must not(contain(password))
    }
  }
}
