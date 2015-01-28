package eventstore
package util

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
    val random = java.security.SecureRandom.getInstance("SHA1PRNG")
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
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

    def isValid(password: Password, hash: Hash, salt: Salt) = {
      def isValid(hash: Array[Byte], salt: Array[Byte]): Boolean = {
        val spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray, salt, 1000, hash.length * 8)
        val test = factory.generateSecret(spec).getEncoded
        val diff = hash.length ^ test.length
        (hash zip test).foldLeft(diff) { case (d, (x, y)) => d | x ^ y } == 0
      }
      isValid(base64.decode(hash), base64.decode(salt))
    }
  }
}