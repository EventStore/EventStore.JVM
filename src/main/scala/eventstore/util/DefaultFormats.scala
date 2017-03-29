package eventstore
package util

import akka.util.{ ByteIterator, ByteStringBuilder }
import java.nio.ByteOrder.{ BIG_ENDIAN, LITTLE_ENDIAN }

object DefaultFormats extends DefaultFormats

trait DefaultFormats {

  implicit object UuidFormat extends BytesFormat[Uuid] {
    private val length = 16

    // This is necessary because of an issue with Protobuf in .NET.
    // See also https://github.com/EventStore/EventStore.JVM/issues/78
    def inverseBitMagic(mostSignificant: Long): Long = {
      val a: Long = (mostSignificant >> 16) & 0xFFFF
      val b: Long = (mostSignificant >> 48) & 0xFFFF
      val c: Long = (mostSignificant >> 32) & 0xFFFF
      val d: Long = mostSignificant & 0xFFFF

      (a << 48) | (d << 32) | (c << 16) | b
    }

    def bitMagic(mostSignificant: Long): Long = {
      val a: Long = mostSignificant & 0xFFFF
      val b: Long = (mostSignificant >> 16) & 0xFFFF
      val c: Long = (mostSignificant >> 48) & 0xFFFF
      val d: Long = (mostSignificant >> 32) & 0xFFFF

      (a << 48) | (b << 32) | (c << 16) | d
    }

    def write(x: Uuid, builder: ByteStringBuilder) = {
      val mostSignificant = bitMagic(x.getMostSignificantBits)
      val leastSignificant = x.getLeastSignificantBits
      builder.putLong(mostSignificant)(LITTLE_ENDIAN)
      builder.putLong(leastSignificant)(BIG_ENDIAN)
    }

    def read(bi: ByteIterator) = {
      val length = bi.len
      require(length >= this.length, s"cannot parse uuid, actual length: $length, expected: ${this.length}")
      val mostSignificant = inverseBitMagic(bi.getLong(LITTLE_ENDIAN))
      val leastSignificant = bi.getLong(BIG_ENDIAN)
      new Uuid(mostSignificant, leastSignificant)
    }
  }

}
