package eventstore

import ContentType._

class ContentTypeITest extends TestConnection {
  // TODO report issue to @gregoryyoung
  val broken = "Does not work in 3.0.0rc9, EventStore always returns Data's ContentType for Metadata"

  "content type" should {
    "be correctly stored and retrieved for Binary/Binary" in new ContentTypeScope {
      verify(Binary, Binary)
    }

    "be correctly stored and retrieved for Json/Json" in new ContentTypeScope {
      verify(Json, Json)
    }

    "be correctly stored and retrieved for Binary/Json" in new ContentTypeScope {
      verify(Binary, Json)
    }.pendingUntilFixed(broken)

    "be correctly stored and retrieved for Json/Binary" in new ContentTypeScope {
      verify(Json, Binary)
    }.pendingUntilFixed(broken)
  }

  private trait ContentTypeScope extends TestConnectionScope {
    def render(x: ContentType) = x.toString.replace("ContentType.", "")

    def content(x: ContentType): Content = {
      val value = x match {
        case ContentType.Json => ByteString(s"""{"test":"test"}""")
        case _                => ByteString(Array[Byte](0, 1, 2))
      }
      Content(value, x)
    }

    def verify(dataContentType: ContentType, metadataContentType: ContentType) = {
      val expected = EventData(
        eventType = s"${render(dataContentType)}/${render(metadataContentType)}",
        data = content(dataContentType),
        metadata = content(metadataContentType)
      )

      val number = append(expected).number
      val actual = readEventCompleted(number).data
      actual mustEqual expected
    }
  }
}