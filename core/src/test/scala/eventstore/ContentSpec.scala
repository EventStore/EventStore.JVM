package eventstore

import org.specs2.mutable.Specification
import testutil.byteStringInt8

class ContentSpec extends Specification {
  "Content.toString" should {
    "return readable string if ContentType = Json" in {
      Content.Json("test").toString mustEqual "Content(test,ContentType.Json)"
    }

    "return empty data if ContentType = Json and value is empty" in {
      Content.Json("").toString mustEqual "Content(ByteString(),ContentType.Json)"
    }

    "return empty data if ContentType = Binary and value is empty" in {
      Content("").toString mustEqual "Content(ByteString(),ContentType.Binary)"
    }

    "return full value if ContentType = Binary and there are not so many bytes" in {
      Content(byteStringInt8(0, 1, 2)).toString mustEqual "Content(ByteString(0,1,2),ContentType.Binary)"
    }

    "return part of value if ContentType = Binary and there are many bytes" in {
      val elems = (1 to 100)
      Content(byteStringInt8((elems :+ 101):_*)).toString mustEqual(s"Content(ByteString(${elems.mkString(",")},..),ContentType.Binary)")
    }
  }
}
