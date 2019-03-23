package eventstore
package core
package util

import org.specs2.mutable.Specification
import util.uuid.randomUuid

class PasswordHashAlgorithmSpec extends Specification {

  "PasswordHashAlgorithm" should {

    "generate hash and salt" in {
      val pha = PasswordHashAlgorithm()
      val password = randomUuid.toString
      val (hash, salt) = pha.hash(password)

      pha.isValid(password, hash, salt) must beTrue
    }
  }
}
