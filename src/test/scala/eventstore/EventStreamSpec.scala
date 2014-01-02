package eventstore

import org.specs2.mutable.Specification

class EventStreamSpec extends Specification {
  "EventStream" should {

    "return Metadata if value starts with $$" in {
      EventStream("$$metadata") mustEqual EventStream.Metadata("metadata")
    }

    "return System if value starts with $" in {
      EventStream("$system") mustEqual EventStream.System("system")
    }

    "return Plain if not starts with $" in {
      EventStream("plain") mustEqual EventStream.Plain("plain")
    }

    "throw exception if starts with $$$$" in {
      EventStream("$$$stream") must not(throwA[IllegalArgumentException])
      EventStream("$$$$stream") must throwA[IllegalArgumentException]
    }
  }

  "EventStream.HasMetadata" should {
    "return System if value starts with $" in {
      EventStream.HasMetadata("$system") mustEqual EventStream.System("system")
    }

    "return Plain if not starts with $" in {
      EventStream.HasMetadata("plain") mustEqual EventStream.Plain("plain")
    }

    "throw exception if starts with $$" in {
      EventStream.HasMetadata("$$stream") must throwA[IllegalArgumentException]
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
      stream.metadata.owner mustEqual stream
    }

    "be not system stream" in {
      stream.isSystem must beFalse
    }

    "be not metadata stream" in {
      stream.isMetadata must beFalse
    }

    "throw exception if starts with $" in {
      EventStream.Plain("$stream") must throwA[IllegalArgumentException]
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
      stream.metadata.owner mustEqual stream
    }

    "be system stream" in {
      stream.isSystem must beTrue
    }

    "be not metadata stream" in {
      stream.isMetadata must beFalse
    }

    "throw exception if starts with $" in {
      EventStream.System("$stream") must throwA[IllegalArgumentException]
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

    "return proper owner" in {
      stream.owner mustEqual EventStream.Plain("metadata")
      EventStream.Metadata("$metadata").owner mustEqual EventStream.System("metadata")
    }

    "be not system stream" in {
      stream.isSystem must beFalse
    }

    "be metadata stream" in {
      stream.isMetadata must beTrue
    }

    "throw exception if starts with $$" in {
      EventStream.System("$$stream") must throwA[IllegalArgumentException]
    }
  }
}
