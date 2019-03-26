package eventstore

import org.specs2.mutable.Specification
import EventStream._

class EventStreamSpec extends Specification {

  "EventStream" should {

    "return All if value is empty" in {
      EventStream("") mustEqual All
    }

    "return All if value is null" in {
      EventStream(null: String) mustEqual All
    }

    "return Id for credentials" in {
      EventStream(UserCredentials.DefaultAdmin) mustEqual EventStream.Id(UserCredentials.DefaultAdmin)
    }

    "return Metadata if value starts with $$" in {
      EventStream($$("metadata")) mustEqual EventStream.Metadata("metadata")
    }

    "return System if value starts with $" in {
      EventStream($("system")) mustEqual EventStream.System("system")
    }

    "return Plain if not starts with $" in {
      EventStream("plain") mustEqual EventStream.Plain("plain")
    }

    "throw exception if starts with $$$$" in {
      EventStream($$$("stream"))  must not(throwA[IllegalArgumentException])
      EventStream($$$$("stream")) must throwA[IllegalArgumentException]
    }
  }

  "EventStream.Id" should {

    "throw exception if value is null" in {
      EventStream.Id(null: String) must throwA[IllegalArgumentException]
    }

    "throw exception if value is empty" in {
      EventStream.Id("") must throwA[IllegalArgumentException]
    }
  }

  "EventStream.HasMetadata" should {

    "return System if value starts with $" in {
      EventStream.HasMetadata($("system")) mustEqual EventStream.System("system")
    }

    "return Plain if not starts with $" in {
      EventStream.HasMetadata("plain") mustEqual EventStream.Plain("plain")
    }

    "throw exception if starts with $$" in {
      EventStream.HasMetadata($$("stream")) must throwA[IllegalArgumentException]
    }

    "throw exception if value is null" in {
      EventStream.HasMetadata(null) must throwA[IllegalArgumentException]
    }

    "throw exception if value is empty" in {
      EventStream.HasMetadata("") must throwA[IllegalArgumentException]
    }
  }

  "EventStream.All" should {

    "be system stream" in {
      EventStream.All.isSystem must beTrue
    }

    "be not metadata stream" in {
      EventStream.All.isMetadata must beFalse
    }
  }

  "EventStream.Plain" should {

    val stream = EventStream.Plain("plain")

    "return proper streamId" in {
      stream.streamId mustEqual "plain"
    }

    "return proper prefix" in {
      stream.prefix mustEqual ""
    }

    "return proper Metadata" in {
      stream.metadata mustEqual EventStream.Metadata("plain")
      stream.metadata.original mustEqual stream
    }

    "be not system stream" in {
      stream.isSystem must beFalse
    }

    "be not metadata stream" in {
      stream.isMetadata must beFalse
    }

    "throw exception if starts with $" in {
      EventStream.Plain($("system")) must throwA[IllegalArgumentException]
    }

    "throw exception if value is null" in {
      EventStream.Plain(null) must throwA[IllegalArgumentException]
    }

    "throw exception if value is empty" in {
      EventStream.Plain("") must throwA[IllegalArgumentException]
    }
  }

  "EventStream.System" should {

    val stream = EventStream.System("system")

    "return proper streamId" in {
      stream.streamId mustEqual "$system"
    }

    "return proper prefix" in {
      stream.prefix mustEqual "$"
    }

    "return proper Metadata" in {
      stream.metadata mustEqual EventStream.Metadata("$system")
      stream.metadata.original mustEqual stream
    }

    "be system stream" in {
      stream.isSystem must beTrue
    }

    "be not metadata stream" in {
      stream.isMetadata must beFalse
    }

    "throw exception if starts with $" in {
      EventStream.System($("system")) must throwA[IllegalArgumentException]
    }

    "throw exception if value is null" in {
      EventStream.System(null: String) must throwA[IllegalArgumentException]
    }

    "throw exception if value is empty" in {
      EventStream.System("") must throwA[IllegalArgumentException]
    }
  }

  "EventStream.Metadata" should {

    val stream = EventStream.Metadata("metadata")

    "return proper streamId" in {
      stream.streamId mustEqual "$$metadata"
    }

    "return proper prefix" in {
      stream.prefix mustEqual "$$"
    }

    "return proper original" in {
      stream.original mustEqual EventStream.Plain("metadata")
      EventStream.Metadata($("metadata")).original mustEqual EventStream.System("metadata")
    }

    "be not system stream" in {
      stream.isSystem must beFalse
    }

    "be metadata stream" in {
      stream.isMetadata must beTrue
    }

    "throw exception if starts with $$" in {
      EventStream.Metadata($$("stream")) must throwA[IllegalArgumentException]
    }

    "throw exception if value is null" in {
      EventStream.Metadata(null) must throwA[IllegalArgumentException]
    }

    "throw exception if value is empty" in {
      EventStream.Metadata("") must throwA[IllegalArgumentException]
    }
  }

  def $(str: String, times: Int = 1): String = s"${"$" * times}$str"
  def $$(str: String): String                = $(str, 2)
  def $$$(str: String): String               = $(str, 3)
  def $$$$(str: String): String              = $(str, 4)
}
