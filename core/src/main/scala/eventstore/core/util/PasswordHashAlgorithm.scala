package eventstore
package core
package util

import java.nio.charset.StandardCharsets
import java.util.Base64

trait PasswordHashAlgorithm {
  type Hash = String
  type Salt = String
  type Password = String

  def hash(password: Password): (Hash, Salt)
  def isValid(password: Password, hash: Hash, salt: Salt): Boolean
}

object PasswordHashAlgorithm {
  def apply(): PasswordHashAlgorithm = Rfc2898

  private object Rfc2898 extends PasswordHashAlgorithm {

    private val encoder64 = Base64.getEncoder
    private val decoder64 = Base64.getDecoder

    private def encode64(value: Array[Byte]) =
      new String(encoder64.encode(value), StandardCharsets.UTF_8)

    private def decode64(value: String): Array[Byte] =
      decoder64.decode(value.getBytes(StandardCharsets.UTF_8))

    val random = java.security.SecureRandom.getInstance("SHA1PRNG")
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    def hash(password: String): (Hash, Salt) = {
      val salt = new Array[Byte](16)
      synchronized {
        random.nextBytes(salt)
      }
      val spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray, salt, 1000, 20 * 8)
      val hash = factory.generateSecret(spec).getEncoded
      encode64(hash) -> encode64(salt)
    }

    def isValid(password: Password, hash: Hash, salt: Salt) = {
      def isValid(hash: Array[Byte], salt: Array[Byte]): Boolean = {
        val spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray, salt, 1000, hash.length * 8)
        val test = factory.generateSecret(spec).getEncoded
        val diff = hash.length ^ test.length
        (hash zip test).foldLeft(diff) { case (d, (x, y)) => d | x ^ y } == 0
      }
      isValid(decode64(hash), decode64(salt))
    }
  }
}