package eventstore
package util

trait PasswordHashAlgorithm {
  type Hash = String
  type Salt = String

  def hash(password: String): (Hash, Salt)
}

object PasswordHashAlgorithm {
  def apply(): PasswordHashAlgorithm = Rfc2898PasswordHashAlgorithm

  private object Rfc2898PasswordHashAlgorithm extends PasswordHashAlgorithm {
    val random = java.security.SecureRandom.getInstance("SHA1PRNG")
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    val base64 = new org.apache.commons.codec.binary.Base64()

    def hash(password: String): (Hash, Salt) = {
      val salt = new Array[Byte](16)
      synchronized {
        random.nextBytes(salt)
      }
      val spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray, salt, 1000, 20 * 8)
      val hash = factory.generateSecret(spec).getEncoded
      base64.encodeToString(hash) -> base64.encodeToString(salt)
    }
  }
}