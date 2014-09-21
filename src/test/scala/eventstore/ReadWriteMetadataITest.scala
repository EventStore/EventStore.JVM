package eventstore

class ReadWriteMetadataITest extends TestConnection {
  "write metadata" should {
    "succeed if stream does not exist" in new MetadataScope {
      actor ! WriteEvents.StreamMetadata(streamId.metadata, metadata)
      expectMsgType[WriteEventsCompleted]
    }

    "succeed if stream does exist" in new MetadataScope {
      appendEventToCreateStream()
      actor ! WriteEvents.StreamMetadata(streamId.metadata, metadata)
      expectMsgType[WriteEventsCompleted]
    }
  }

  "read metadata" should {
    "fail if no metadata stream" in new MetadataScope {
      actor ! ReadEvent.StreamMetadata(streamId.metadata)
      expectException() mustEqual EsError.StreamNotFound
    }

    "succeed if metadata stream exists" in new MetadataScope {
      actor ! WriteEvents.StreamMetadata(streamId.metadata, metadata)
      expectMsgType[WriteEventsCompleted]

      actor ! ReadEvent.StreamMetadata(streamId.metadata)
      expectMsgPF() {
        case ReadEventCompleted(Event.StreamMetadata(`metadata`)) => ok
      }
    }
  }

  trait MetadataScope extends TestConnectionScope {
    val metadata = Content.Json("""{"test":"test"}""")
  }
}
