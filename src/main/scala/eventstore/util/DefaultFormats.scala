package eventstore
package util

import akka.util.{ ByteIterator, ByteStringBuilder }

object DefaultFormats extends DefaultFormats

trait DefaultFormats {

  implicit object UuidFormat extends BytesFormat[Uuid] {
    private val length = 16
    implicit val order = java.nio.ByteOrder.LITTLE_ENDIAN

    def write(x: Uuid, builder: ByteStringBuilder) = {
      builder.putLong(x.getMostSignificantBits)
      builder.putLong(x.getLeastSignificantBits)
    }

    def read(bi: ByteIterator) = {
      val length = bi.len
      require(length >= this.length, s"cannot parse uuid, actual length: $length, expected: ${this.length}")
      new Uuid(bi.getLong, bi.getLong)
    }
  }

}
