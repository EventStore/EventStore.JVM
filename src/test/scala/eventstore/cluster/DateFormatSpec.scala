package eventstore.cluster

import org.joda.time.{ DateTime, DateTimeZone }
import org.specs2.mutable.Specification
import eventstore.cluster.ClusterProtocol.DateTimeFormat
import spray.json._

class DateFormatSpec extends Specification {
  "DateFormat" should {
    "parse 2015-01-26T19:52:40Z" in {
      val expected = new DateTime(2015, 1, 26, 19, 52, 40, DateTimeZone.UTC)
      DateTimeFormat.read(JsString("2015-01-26T19:52:40Z")) mustEqual expected
    }

    "parse 2014-09-24T19:53:20.035753Z" in {
      val expected = new DateTime(2014, 9, 24, 19, 53, 20, 35, DateTimeZone.UTC)
      DateTimeFormat.read(JsString("2014-09-24T19:53:20.035753Z")) mustEqual expected
    }

    "parse 2015-01-29T12:28:54.8302665Z" in {
      val expected = new DateTime(2015, 1, 29, 12, 28, 54, 830, DateTimeZone.UTC)
      DateTimeFormat.read(JsString("2015-01-29T12:28:54.8302665Z")) mustEqual expected
    }
  }
}
