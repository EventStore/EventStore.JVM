package eventstore
package j

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Awaitable }
import util.ActorSpec

class EsConnectionITest extends ActorSpec {
  "EsConnection" should {

    "write events" in new TestScope {
      await(connection.writeEvents("java-writeEvents-" + newUuid, null, events, null))
    }

    "delete stream" in new TestScope {
      val streamId = "java-deleteStream-" + newUuid
      await(connection.writeEvents(streamId, null, events, null))
      await(connection.deleteStream(streamId, null, null))
    }

    "read event" in new TestScope {
      val streamId = "java-readEvent-" + newUuid
      try await(connection.readEvent(streamId, null, resolveLinkTos = false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }

      await(connection.writeEvents(streamId, null, events, null))
      val event = await(connection.readEvent(streamId, null, resolveLinkTos = false, null))

      event.data mustEqual eventData
      event.streamId mustEqual EventStream(streamId)
      event.number mustEqual EventNumber.First
    }

    "read stream events forward" in new TestScope {
      val streamId = "java-readStreamForward-" + newUuid

      try await(connection.readStreamEventsForward(streamId, null, 10, resolveLinkTos = false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await(connection.writeEvents(streamId, null, events, null))
      val result = await(
        connection.readStreamEventsForward(streamId, new EventNumber.Exact(0), 10, resolveLinkTos = false, null))

      result.direction mustEqual ReadDirection.forward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Exact(1)

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read stream events backward" in new TestScope {
      val streamId = "java-readStreamBackward-" + newUuid
      try await(connection.readStreamEventsBackward(streamId, null, 10, resolveLinkTos = false, null)) catch {
        case EsException(eventstore.EsError.StreamNotFound, _) =>
      }
      await(connection.writeEvents(streamId, null, events, null))
      val result = await {
        connection.readStreamEventsBackward(streamId, null, 10, resolveLinkTos = false, null)
      }
      result.direction mustEqual ReadDirection.backward
      result.lastEventNumber mustEqual EventNumber.Exact(0)
      result.nextEventNumber mustEqual EventNumber.Last

      foreach(result.events)(_.data mustEqual eventData)
    }

    "read all events forward" in new TestScope {
      val result = await(connection.readAllEventsForward(null, 10, resolveLinkTos = false, null))
      result.direction mustEqual ReadDirection.forward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }
    }

    "read all events backward" in new TestScope {
      val result = await(connection.readAllEventsBackward(null, 10, resolveLinkTos = false, null))
      result.direction mustEqual ReadDirection.backward

      result.events.foreach {
        event =>
          val data = event.event.data
          if (data.eventType == eventData.eventType) data mustEqual eventData
      }
    }
  }

  trait TestScope extends ActorScope {
    val connection = new EsConnectionImpl(eventstore.EsConnection(system))

    val eventData = EventData(eventType = "java-test", data = Content("data"), metadata = Content("metadata"))

    val events = List(eventData).asJava

    def await[T](awaitable: Awaitable[T]): T = Await.result(awaitable, 2.seconds)
  }
}
