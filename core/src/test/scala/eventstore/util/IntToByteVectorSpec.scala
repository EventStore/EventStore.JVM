package eventstore
package util

import org.specs2.mutable.Specification
import scodec.bits.ByteOrdering
import eventstore.syntax._
import eventstore.util.IntToByteVector._

class IntToByteVectorSpec extends Specification {

  "IntToByteVector" should {

    "roundtrip (uint8)" in {
      (0 to 255).map(i ⇒ uint8(i).unsafe.take(1).toInt(signed = false, ordering = ByteOrdering.BigEndian) shouldEqual i)
    }

    "support endianess correctly (uint8)" in {
      val uint8L = new IntToByteVector(8, false, ByteOrdering.LittleEndian).encode _
      (0 to 255).map(i ⇒ uint8L(i).unsafe shouldEqual uint8(i).unsafe.reverse)
    }

    "return an error when value to apply is out of legal range (uint8)" in {
      uint8(256) shouldEqual Left("256 is greater than maximum value 255 for 8-bit unsigned integer")
      uint8(-1) shouldEqual Left("-1 is less than minimum value 0 for 8-bit unsigned integer")
    }
  }

}