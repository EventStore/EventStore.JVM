package eventstore

import ContentType._

/**
 * @author Yaroslav Klymko
 */
class ContentTypeITest extends TestConnection {
  "content type" should {
    "be received " in new TestConnectionScope {
      val contentTypes = Unknown(Known.last.value + 1) :: Known.toList

      for {
        dataContentType <- contentTypes
        metadataContentType <- contentTypes
      } yield {
        val number = append(EventData(
          newUuid, s"$dataContentType/$metadataContentType",
          //          dataContentType = dataContentType,
          data = ByteString(s"""{"data":"data", "contentType":"$dataContentType"}"""),
          //          metadataContentType = metadataContentType,
          metadata = ByteString(s"""{"metadata":"metadata", "contentType":"$metadataContentType"}"""))).number

        val actual = readEventSucceed(number).data
        //        actual.dataContentType mustEqual dataContentType
        //        actual.metadataContentType mustEqual metadataContentType
        todo
      }
    }
  }
}
