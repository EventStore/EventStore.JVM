package eventstore
package util

import scodec.bits.{BitVector, ByteOrdering, ByteVector}
import eventstore.syntax.Attempt

// This code is inspired by scodec's `IntCodec`, see:
// https://github.com/scodec/scodec/blob/series/1.11.x/shared/src/main/scala/scodec/codecs/IntCodec.scala

private[eventstore] final class IntToByteVector(bits: Int, signed: Boolean, ordering: ByteOrdering) {

  require(bits > 0 && bits <= (if (signed) 32 else 31),
    "bits must be in range [1, 32] for signed and [1, 31] for unsigned"
  )

  val MaxValue = (1 << (if (signed) bits - 1 else bits)) - 1
  val MinValue = if (signed) -(1 << (bits - 1)) else 0

  private def description = s"$bits-bit ${if (signed) "signed" else "unsigned"} integer"

  def encode(i: Int): Attempt[ByteVector] = {
    if (i > MaxValue) {
      Left(s"$i is greater than maximum value $MaxValue for $description")
    } else if (i < MinValue) {
      Left(s"$i is less than minimum value $MinValue for $description")
    } else {
      Right(BitVector.fromInt(i, bits, ordering).toByteVector)
    }
  }

  override def toString: String = description
}

private[eventstore] object IntToByteVector {
  val uint8: Int ⇒ Attempt[ByteVector] = new IntToByteVector(8, false, ByteOrdering.BigEndian).encode _
}