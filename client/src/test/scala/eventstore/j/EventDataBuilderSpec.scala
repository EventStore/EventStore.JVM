package eventstore
package j

import org.specs2.mutable.Specification

class EventDataBuilderSpec extends Specification {

  "EventDataBuilder" should {
    "build binary data" in {
      val eventId = randomUuid
      val eventType = "binary"
      val data = Array[Byte](1, 2, 3, 4)
      val metadata = Array[Byte](5, 6, 7, 8)

      val expected = EventData(eventType, eventId, data = Content(data), metadata = Content(metadata))

      val actual = new EventDataBuilder(eventType)
        .eventId(eventId)
        .data(data)
        .metadata(metadata)
        .build

      actual mustEqual expected
    }

    "build string data" in {
      val eventId = randomUuid
      val eventType = "string"
      val data = "data"
      val metadata = "metadata"

      val expected = EventData(eventType, eventId, data = Content(data), metadata = Content(metadata))

      val actual = new EventDataBuilder(eventType)
        .eventId(eventId)
        .data(data)
        .metadata(metadata)
        .build

      actual mustEqual expected
    }

    "build json data" in {
      val eventId = randomUuid
      val eventType = "json"
      val data = "{\"data\":\"data\"}"
      val metadata = "{\"metadata\":\"metadata\"}"

      val expected = EventData(eventType, eventId, data = Content.Json(data), metadata = Content.Json(metadata))

      val actual = new EventDataBuilder(eventType)
        .eventId(eventId)
        .jsonData(data)
        .jsonMetadata(metadata)
        .build

      actual mustEqual expected
    }
  }
}