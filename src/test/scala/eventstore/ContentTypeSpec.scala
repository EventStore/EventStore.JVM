package eventstore

import org.specs2.mutable.Specification
import ContentType._

class ContentTypeSpec extends Specification {
  "ContentType" should {

    /*TODO not yet implemented in EventStore 2.0.1
    "throw proper exception when using known values as unknown" in foreach(Known) {
      x =>
        Unknown(x.value) must throwAn[IllegalArgumentException].like {
          case e => e.getMessage must contain(x.toString)
        }
    }*/

    /*"throw exception when illegal value" in {
      ContentType(Known.head.value - 1) must throwAn[IllegalArgumentException]
    }*/

    "return ContentType.Known instance for known values" in foreach(Known) {
      x => ContentType(x.value) mustEqual x
    }

    /*TODO not yet implemented in EventStore 2.0.1
    "return ContentType.Unknown instance for unknown values" in {
      val unknown = Known.last.value + 1
      ContentType(unknown) mustEqual ContentType.Unknown(unknown)
    }*/
  }
}
