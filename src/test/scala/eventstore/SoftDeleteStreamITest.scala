package eventstore

class SoftDeleteStreamITest extends TestConnection {
  implicit def direction: ReadDirection = ReadDirection.Forward

  "soft delete" should {
    "succeed for existing stream" in new SoftDeleteScope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      readStreamEventsFailed must throwA[StreamNotFoundException]
    }

    "allow recreation of stream if expected version is any" in new SoftDeleteScope {
      allowRecreation(ExpectedVersion.Any)
    }

    "allow recreation of stream if expected version is no stream" in new SoftDeleteScope {
      allowRecreation(ExpectedVersion.NoStream)
    }

    "allow recreation of stream if expected version is exact" in new SoftDeleteScope {
      allowRecreation(ExpectedVersion.Exact(0))
    }

    "allow hard delete" in new SoftDeleteScope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      deleteStream(hard = true)
      readStreamEventsFailed must throwA[StreamDeletedException]

      actor ! WriteEvents(streamId, List(newEventData))
      expectEsException() must throwA[StreamDeletedException]
    }

    "allow recreation only for first write when expected version is no stream" in new SoftDeleteScope {
      val event = allowRecreation(ExpectedVersion.NoStream)
      actor ! WriteEvents(streamId, List(newEventData), ExpectedVersion.NoStream)
      expectEsException() must throwA[WrongExpectedVersionException]
      streamEvents.toList mustEqual List(event)
    }

    "allow recreation for both writes when expected version is any" in new SoftDeleteScope {
      val event1 = allowRecreation(ExpectedVersion.Any)
      val event2 = newEventData
      write(ExpectedVersion.Any, event2)
      streamEvents.toList mustEqual List(event1, event2)
    }

    "preserve metadata except $tb when recreated" in new SoftDeleteScope {
      appendEventToCreateStream()
      // Long.MaxValue = 9223372036854775807
      writeMetadata("""{"$tb":9223372036854775807,"test":"test"}""")
      appendEventToRecreate()
      readMetadata() mustEqual """{"$tb":1,"test":"test"}"""
    }

    "preserve metadata except $tb when recreated via DeleteStream command" in new SoftDeleteScope {
      appendEventToCreateStream()
      writeMetadata("""{"test":"test"}""")
      deleteStream(hard = false)
      appendEventToRecreate()
      readMetadata() mustEqual """{"$tb":1,"test":"test"}"""
    }.pendingUntilFixed // TODO why this does not work?

    "allow setting json metadata on empty soft deleted stream and recreate stream not overriding metadata" in new SoftDeleteScope {
      deleteStream(hard = false)
      writeMetadata("""{"test":"test"}""")
      readMetadata() mustEqual """{"test":"test","$tb":0}"""
    }

    "allow setting json metadata on nonempty soft deleted stream and recreate stream not overriding metadata" in new SoftDeleteScope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      writeMetadata("""{"test":"test"}""")
      readMetadata() mustEqual """{"test":"test","$tb":1}"""
    }

    "allow setting nonjson metadata on empty soft deleted stream and recreate stream" in new SoftDeleteScope {
      deleteStream(hard = false)
      val metadata = writeMetadataBinary(1, 2, 3, 4)
      readStreamEventsFailed must throwA[StreamNotFoundException]
      readMetadataBinary() mustEqual metadata
    }

    "allow setting nonjson metadata on nonempty soft deleted stream and recreate" in new SoftDeleteScope {
      val event = appendEventToCreateStream()
      deleteStream(hard = false)
      val metadata = writeMetadataBinary(1, 2, 3, 4)
      lastEvent.data mustEqual event
      readMetadataBinary() mustEqual metadata
    }
  }

  private trait SoftDeleteScope extends TestConnectionScope {
    def write(expVer: ExpectedVersion, events: EventData*): Unit = {
      actor ! WriteEvents(streamId, events.toList, expVer)
      val completed = expectMsgType[WriteEventsCompleted]
      completed.position must beSome
    }

    def allowRecreation(expVer: ExpectedVersion): EventData = {
      appendEventToCreateStream()
      deleteStream(hard = false)
      appendEventToRecreate(expVer)
    }

    def lastEvent: Event = {
      actor ! ReadEvent(streamId, EventNumber.Last)
      expectMsgType[ReadEventCompleted].event
    }

    def appendEventToRecreate(expVer: ExpectedVersion = ExpectedVersion.Any): EventData = {
      val event = newEventData
      write(expVer, event)
      lastEvent.data mustEqual event
      event
    }

    def readMetadata(): String = {
      actor ! ReadEvent.StreamMetadata(streamId.metadata)
      expectMsgPF() {
        case ReadEventCompleted(Event.StreamMetadata(Content.Json(x))) => x
      }
    }

    def writeMetadata(metadata: String): Unit = {
      actor ! WriteEvents.StreamMetadata(streamId.metadata, Content.Json(metadata))
      expectMsgType[WriteEventsCompleted]
    }

    def readMetadataBinary(): ByteString = {
      actor ! ReadEvent.StreamMetadata(streamId.metadata)
      expectMsgPF() {
        case ReadEventCompleted(Event.StreamMetadata(x)) => x.value
      }
    }

    def writeMetadataBinary(metadata: Byte*): ByteString = {
      val bs = ByteString(metadata.toArray)
      actor ! WriteEvents.StreamMetadata(streamId.metadata, Content(bs))
      expectMsgType[WriteEventsCompleted]
      bs
    }
  }
}