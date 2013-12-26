package eventstore

import ContentType._

class ContentTypeITest extends TestConnection {
  "content type" should {
    "be correctly stored and retrieved" in new TestConnectionScope {
      val contentTypes = /*Unknown(Known.last.value + 1) :: TODO*/ Known.toList
      val variants = for {
        dataContentType <- contentTypes
        metadataContentType <- contentTypes
      } yield dataContentType -> metadataContentType

      def content(x: ContentType): Content = Content(x match {
        case ContentType.Json   => ByteString(s"""{"data":"data", "contentType":"$x"}""")
        case ContentType.Binary => ByteString(Array[Byte](0, 1, 2, 3, 4, 5, 6, 7))
      }, x)

      foreach(variants) {
        case (dataContentType, metadataContentType) =>
          val expected = EventData(
            eventType = s"$dataContentType/$metadataContentType",
            data = content(dataContentType),
            metadata = content(metadataContentType))

          val number = append(expected).number
          val actual = readEventCompleted(number).data
          actual mustEqual expected
      }
    }.pendingUntilFixed
  }
}
