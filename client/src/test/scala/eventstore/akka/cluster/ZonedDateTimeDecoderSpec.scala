package eventstore
package akka
package cluster

import java.time._
import org.specs2.mutable.Specification
import io.circe._
import CirceDecoders._

class ZonedDateTimeDecoderSpec extends Specification {

  "DateFormat" should {

    "parse 2015-01-26T19:52:40Z" in {
      val str = "2015-01-26T19:52:40Z"
      val expected = ZonedDateTime.of(2015, 1, 26, 19, 52, 40, 0, ZoneOffset.UTC)
      codecForZDT(Json.fromString(str).hcursor).toOption must beSome(expected)
    }

    "parse 2014-09-24T19:53:20.035753Z" in {
      val str = "2014-09-24T19:53:20.035753Z"
      val expected = ZonedDateTime.of(2014, 9, 24, 19, 53, 20, 35753000, ZoneOffset.UTC)
      codecForZDT(Json.fromString(str).hcursor).toOption must beSome(expected)
    }

    "parse 2015-01-29T12:28:54.8302665Z" in {
      val str = "2015-01-29T12:28:54.8302665Z"
      val expected = ZonedDateTime.of(2015, 1, 29, 12, 28, 54, 830266500, ZoneOffset.UTC)
      codecForZDT(Json.fromString(str).hcursor).toOption must beSome(expected)
    }

    "parse 2017-03-26T02:28:54.830Z" in {
      val str = "2017-03-26T02:28:54.830Z"
      val expected = ZonedDateTime.of(2017, 3, 26, 2, 28, 54, 830 * 1000000, ZoneOffset.UTC)
      codecForZDT(Json.fromString(str).hcursor).toOption must beSome(expected)
    }

    "parse 2019-03-11T11:44:59.034Z" in {
      val str = "2019-03-11T11:44:59.034Z"
      val expected = ZonedDateTime.of(2019, 3, 11, 11, 44, 59, 34 * 1000000, ZoneOffset.UTC)
      codecForZDT(Json.fromString(str).hcursor).toOption must beSome(expected)
    }

  }
}
