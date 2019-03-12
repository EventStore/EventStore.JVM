package eventstore
package util

import scodec.bits.{ByteOrdering, ByteVector}

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

    def write(x: Uuid): ByteVector = {
      val mostSignificant = bitMagic(x.getMostSignificantBits)
      val leastSignificant = x.getLeastSignificantBits
      ByteVector.fromLong(mostSignificant, size = 8, ByteOrdering.LittleEndian) ++
      ByteVector.fromLong(leastSignificant, size = 8, ByteOrdering.BigEndian)
    }

    def read(bi: ByteVector): Attempt[ReadResult[Uuid]] = {

      def fail: String = s"cannot parse uuid, actual length: $length, expected: ${this.length}"

      def readUuid: ReadResult[Uuid] = {

        val most      = bi.take(8)
        val least     = bi.drop(8).take(8)
        val remainder = bi.drop(16)

        val mostSignificant  = inverseBitMagic(most.toLong(signed = true, ByteOrdering.LittleEndian))
        val leastSignificant = least.toLong(signed = true, ByteOrdering.BigEndian)

        ReadResult(new Uuid(mostSignificant, leastSignificant), remainder)
      }

      Either.cond(bi.length >= this.length, readUuid, fail)

    }

  }
}