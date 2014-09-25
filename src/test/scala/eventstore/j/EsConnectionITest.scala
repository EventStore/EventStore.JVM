package eventstore
package j

import scala.collection.JavaConverters._

class EsConnectionITest extends eventstore.util.ActorSpec {
  "EsConnection" should {

    "write events" in new TestScope {
      await_(connection.writeEvents("java-writeEvents-" + randomUuid, null, events, null))
    }

    "delete stream" in new TestScope {
      val streamId = "java-deleteStream-" + randomUuid
      await_(connection.writeEvents(streamId, null, events, null))
      await_(connection.deleteStream(streamId, null, null))
    }

    "read event" in new TestScope {
      val streamId = "java-readEvent-" + randomUuid
      try await_(connection.readEvent(streamId, null, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }

      await_(connection.writeEvents(streamId, null, events, null))
      val event = await_(connection.readEvent(streamId, null, false, null))

      event.data mustEqual eventData
      event.streamId mustEqual EventStream(streamId)
      event.number mustEqual EventNumber.First
    }

    "read stream events forward" in new TestScope {
      val streamId = "java-readStreamForward-" + randomUuid

      try await_(connection.readStreamEventsForward(streamId, null, 10, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await_(connection.writeEvents(streamId, null, events, null))
      val result = await_(
        connection.readStreamEventsForward(streamId, new EventNumber.Exact(0), 10, false, null))

      result.direction mustEqual ReadDirection.forward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Exact(1)

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read stream events backward" in new TestScope {
      val streamId = "java-readStreamBackward-" + randomUuid
      try await_(connection.readStreamEventsBackward(streamId, null, 10, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await_(connection.writeEvents(streamId, null, events, null))
      val result = await_ {
        connection.readStreamEventsBackward(streamId, null, 10, false, null)
      }
      result.direction mustEqual ReadDirection.backward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Last

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read all events forward" in new TestScope {
      val result = await_(connection.readAllEventsForward(null, 10, false, null))
      result.direction mustEqual ReadDirection.forward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }

      override def eventType = "java-readAllForward"
    }

    "read all events backward" in new TestScope {
      val result = await_(connection.readAllEventsBackward(null, 10, false, null))
      result.direction mustEqual ReadDirection.backward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }

      override def eventType = "java-readAllBackward"
    }

    "start transaction" in new TestScope {
      val streamId = "java-startTransaction-" + randomUuid
      val transaction = for {
        t <- connection.startTransaction(streamId, null, null)
        _ <- t.write(events)
        _ <- t.commit()
      } yield t
      await_(transaction).getId must be_>=(-1L)
    }

    "continue transaction" in new TestScope {
      val streamId = "java-continueTransaction-" + randomUuid
      val result = for {
        started <- connection.startTransaction(streamId, null, null)
        _ <- started.write(events)
        continued = connection.continueTransaction(started.getId, null)
        _ <- continued.write(List(newEventData).asJava)
        _ <- continued.commit()
        _ <- started.commit()
      } yield (started, continued)

      val (started, continued) = await_(result)
      started.getId mustEqual continued.getId
    }

    "set & get stream metadata bytes" in new TestScope {
      val streamId = "java-streamMetadata-" + randomUuid
      val expected = Array[Byte](1, 2, 3)
      def streamMetadataBytes = connection.getStreamMetadataBytes(streamId, null)
      val (noStream, actual, deleted) = await_(for {
        noStream <- streamMetadataBytes
        _ <- connection.setStreamMetadata(streamId, null, expected, null)
        get <- streamMetadataBytes
        // _ <- connection.deleteStream("$$" + streamId, null, UserCredentials.defaultAdmin) TODO AccessDenied returned
        deleted <- streamMetadataBytes
      } yield (noStream, get, deleted))
      noStream must beEmpty
      actual mustEqual expected
      //      deleted must beEmpty
    }
  }

  private trait TestScope extends ActorScope {
    val connection: EsConnection = new EsConnectionImpl(eventstore.EsConnection(system))
    val eventData = newEventData
    val events = List(eventData).asJava

    def eventType = "java-test"
    def newEventData = EventData(eventType = eventType, data = Content("data"), metadata = Content("metadata"))
  }
}
