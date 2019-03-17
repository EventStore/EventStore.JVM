package eventstore
package akka

class StreamMetadataITest extends TestConnection {

  "StreamMetadata" should {

    "return empty content for empty stream" in new TestScope {
      val content = connection.getStreamMetadata(streamId).await_
      content mustEqual Content.Empty
    }

    "return empty content for non empty stream" in new TestScope {
      appendEventToCreateStream()
      val content = connection.getStreamMetadata(streamId).await_
      content mustEqual Content.Empty
    }

    "return empty metadata when stream deleted" in new TestScope {
      appendEventToCreateStream()
      deleteStream()
      val content = connection.getStreamMetadata(streamId).await_
      content mustEqual Content.Empty
    }

    "set stream metadata" in new TestScope {
      appendEventToCreateStream()
      val result = connection.setStreamMetadata(streamId, metadata).await_
      result map { _.nextExpectedVersion } must beSome(ExpectedVersion.Exact(0))
    }

    "return stream metadata" in new TestScope {
      appendEventToCreateStream()
      connection.setStreamMetadata(streamId, metadata).await_
      val content = connection.getStreamMetadata(streamId).await_
      content shouldEqual metadata
    }
  }

  private trait TestScope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)
    val metadata = Content.Json(""" { "test": "test" } """)
  }
}
