package eventstore
package tcp


import java.nio.ByteOrder.LITTLE_ENDIAN
import akka.util.{ByteStringBuilder, ByteIterator}

/**
 * @author Yaroslav Klymko
 */

object UuidSerializer {
  private val length = 16
  implicit val order = LITTLE_ENDIAN

  def serialize(uuid: Uuid): ByteString = {
    val builder = ByteString.newBuilder
    write(builder, uuid)
    builder.result()
  }

  def write(builder: ByteStringBuilder, uuid: Uuid) {
    builder.putLong(uuid.getMostSignificantBits)
    builder.putLong(uuid.getLeastSignificantBits)
  }

  def deserialize(bs: ByteString): Uuid = read(bs.iterator)

  def read(iterator: ByteIterator): Uuid = {
    val length = iterator.len
    require(length >= this.length, s"Can not parse uuid, actual length: $length, expected: ${this.length}")
    new Uuid(iterator.getLong, iterator.getLong)
  }
}
