package eventstore
package j

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Awaitable }
import util.ActorSpec

class EsConnectionITest extends ActorSpec {
  "EsConnection" should {

    "write events" in new TestScope {
      await(connection.writeEvents("java-writeEvents-" + randomUuid, null, events, null))
    }

    "delete stream" in new TestScope {
      val streamId = "java-deleteStream-" + randomUuid
      await(connection.writeEvents(streamId, null, events, null))
      await(connection.deleteStream(streamId, null, null))
    }

    "read event" in new TestScope {
      val streamId = "java-readEvent-" + randomUuid
      try await(connection.readEvent(streamId, null, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }

      await(connection.writeEvents(streamId, null, events, null))
      val event = await(connection.readEvent(streamId, null, false, null))

      event.data mustEqual eventData
      event.streamId mustEqual EventStream(streamId)
      event.number mustEqual EventNumber.First
    }

    "read stream events forward" in new TestScope {
      val streamId = "java-readStreamForward-" + randomUuid

      try await(connection.readStreamEventsForward(streamId, null, 10, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await(connection.writeEvents(streamId, null, events, null))
      val result = await(
        connection.readStreamEventsForward(streamId, new EventNumber.Exact(0), 10, false, null))

      result.direction mustEqual ReadDirection.forward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Exact(1)

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read stream events backward" in new TestScope {
      val streamId = "java-readStreamBackward-" + randomUuid
      try await(connection.readStreamEventsBackward(streamId, null, 10, false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await(connection.writeEvents(streamId, null, events, null))
      val result = await {
        connection.readStreamEventsBackward(streamId, null, 10, false, null)
      }
      result.direction mustEqual ReadDirection.backward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Last

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read all events forward" in new TestScope {
      val result = await(connection.readAllEventsForward(null, 10, false, null))
      result.direction mustEqual ReadDirection.forward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }

      override def eventType = "java-readAllForward"
    }

    "read all events backward" in new TestScope {
      val result = await(connection.readAllEventsBackward(null, 10, false, null))
      result.direction mustEqual ReadDirection.backward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }

      override def eventType = "java-readAllBackward"
    }

    "set & get stream metadata bytes" in new TestScope {
      val streamId = "java-streamMetadata-" + randomUuid
      val expected = Array[Byte](1, 2, 3)
      def streamMetadataBytes = connection.getStreamMetadataBytes(streamId, null)
      val (noStream, actual, deleted) = await(for {
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

  trait TestScope extends ActorScope {
    val connection: EsConnection = new EsConnectionImpl(eventstore.EsConnection(system))
    def eventType = "java-test"
    val eventData = EventData(eventType = eventType, data = Content("data"), metadata = Content("metadata"))

    val events = List(eventData).asJava

    def await[T](awaitable: Awaitable[T]): T = Await.result(awaitable, 2.seconds)
  }
}
