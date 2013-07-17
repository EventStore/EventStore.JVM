package eventstore.client

import org.specs2.mutable.SpecificationWithJUnit

/**
 * @author Yaroslav Klymko
 */
class FrameSpec extends SpecificationWithJUnit {
  val list = List(
    1234 -> Array[Byte](-46, 4, 0, 0),
    1234567890 -> Array[Byte](-46, 2, -106, 73))

  "Frame" should {
//    "size to bytes" in list.foreach {
//      case (size, bytes) => Frame.sizeToBytes(size) mustEqual bytes
//    }
//
//    "bytes to size" in list.foreach {
//      case (size, bytes) => Frame.bytesToSize(bytes) mustEqual size
//    }
    "todo" in todo
  }
}
