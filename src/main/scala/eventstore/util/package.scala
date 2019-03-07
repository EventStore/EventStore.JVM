package eventstore

import scodec.bits.{ByteOrdering, ByteVector}

package object util {

  val uint8: Int ⇒ Attempt[ByteVector] = new IntToByteVector(8, false, ByteOrdering.BigEndian).encode _

}
