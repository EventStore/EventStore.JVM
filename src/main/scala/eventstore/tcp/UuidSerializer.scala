package eventstore.client
package tcp



import java.nio.ByteOrder.{LITTLE_ENDIAN, BIG_ENDIAN}

/**
 * @author Yaroslav Klymko
 */

object UuidSerializer {
  private val length = 16
  implicit val order: java.nio.ByteOrder = LITTLE_ENDIAN

  def serialize(uuid: Uuid): ByteString = {
    val builder = ByteString.newBuilder
    builder.putLong(uuid.getMostSignificantBits)
    builder.putLong(uuid.getLeastSignificantBits)
    builder.result()
  }

  def deserialize(bs: ByteString): Uuid = {
    val length = bs.length
    require(length == 16, s"Can not parse uuid, actual length: $length, expected: ${this.length}")
    val iterator = bs.iterator
    val uuid = new Uuid(iterator.getLong, iterator.getLong)
    uuid
  }
}
