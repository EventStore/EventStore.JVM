package eventstore
package j

import scala.collection.JavaConverters._
import java.io.Closeable
import akka.actor.Status.Failure
import akka.testkit.TestProbe
import scala.concurrent.duration._

class EsConnectionSpec extends util.ActorSpec {

  val streams = List("plain", "$system", "$$metadata")
  val existingVersions = List(ExpectedVersion.Any, ExpectedVersion.First, null)
  val versions = ExpectedVersion.NoStream :: existingVersions
  val contents = List(Content("string"), Content(Array[Byte](1, 2, 3)), Content.Json("{}"))
  val exactNumbers = List(EventNumber.First, null)
  val numbers = EventNumber.Last :: exactNumbers
  val booleans = List(true, false)
  val ints = List(1, 10, 100)
  val exactPositions: List[Position.Exact] = List(Position.First, null)
  val positions = Position.Last :: exactPositions

  val events = (for {
    data <- contents
    metadata <- contents
  } yield EventData("event-type", data = data, metadata = metadata)).asJavaCollection

  val userCredentials = List(UserCredentials.DefaultAdmin, UserCredentials("login", "password"), null)

  "EsConnection" should {
    "write events" in new TestScope {
      for {
        stream <- streams
        version <- versions
        uc <- userCredentials
      } {
        val future = connection.writeEvents(stream, version, events, uc)
        expect(WriteEvents(
          streamId = EventStream.Id(stream),
          events = events.asScala.toList,
          expectedVersion = Option(version) getOrElse ExpectedVersion.Any
        ), uc)
        lastSender ! WriteEventsCompleted(None, None)
        future.await_ mustEqual null
      }
    }

    "delete stream" in new TestScope {
      for {
        stream <- streams
        version <- existingVersions
        uc <- userCredentials
        hd <- booleans
      } {
        val future = connection.deleteStream(stream, version, hd, uc)
        expect(DeleteStream(
          streamId = EventStream.Id(stream),
          expectedVersion = Option(version) getOrElse ExpectedVersion.Any,
          hard = hd
        ), uc)
        lastSender ! DeleteStreamCompleted(None)
        future.await_ mustEqual null
      }
    }

    "read event" in new TestScope {
      for {
        stream <- streams
        number <- numbers
        resolveLinkTos <- booleans
        uc <- userCredentials
      } {
        connection.readEvent(stream, number, resolveLinkTos, uc)
        expect(ReadEvent(
          streamId = EventStream.Id(stream),
          eventNumber = Option(number) getOrElse EventNumber.Last,
          resolveLinkTos = resolveLinkTos
        ), uc)
      }
    }

    "read stream events forward" in new TestScope {
      for {
        stream <- streams
        number <- exactNumbers
        count <- ints
        resolveLinkTos <- booleans
        uc <- userCredentials
      } {
        connection.readStreamEventsForward(stream, number, count, resolveLinkTos, uc)
        expect(ReadStreamEvents(
          streamId = EventStream.Id(stream),
          fromNumber = Option(number) getOrElse EventNumber.First,
          maxCount = count,
          direction = ReadDirection.forward,
          resolveLinkTos = resolveLinkTos
        ), uc)
      }
    }

    "read stream events backward" in new TestScope {
      for {
        stream <- streams
        number <- numbers
        count <- ints
        resolveLinkTos <- booleans
        uc <- userCredentials
      } {
        connection.readStreamEventsBackward(stream, number, count, resolveLinkTos, uc)
        expect(ReadStreamEvents(
          streamId = EventStream.Id(stream),
          fromNumber = Option(number) getOrElse EventNumber.Last,
          maxCount = count,
          direction = ReadDirection.backward,
          resolveLinkTos = resolveLinkTos
        ), uc)
      }
    }

    "read all events forward" in new TestScope {
      for {
        position <- exactPositions
        count <- ints
        resolveLinkTos <- booleans
        uc <- userCredentials
      } {
        connection.readAllEventsForward(position, count, resolveLinkTos, uc)
        expect(ReadAllEvents(
          fromPosition = Option(position) getOrElse Position.First,
          maxCount = count,
          direction = ReadDirection.forward,
          resolveLinkTos = resolveLinkTos
        ), uc)
      }
    }

    "read all events backward" in new TestScope {
      for {
        position <- positions
        count <- ints
        resolveLinkTos <- booleans
        uc <- userCredentials
      } {
        connection.readAllEventsBackward(position, count, resolveLinkTos, uc)
        expect(ReadAllEvents(
          fromPosition = Option(position) getOrElse Position.Last,
          maxCount = count,
          direction = ReadDirection.backward,
          resolveLinkTos = resolveLinkTos
        ), uc)
      }
    }

    "subscribe to stream" in new StreamSubscriptionScope {
      val closeable = connection.subscribeToStream(streamId.streamId, observer, false, null)
      expectMsg(SubscribeTo(streamId))
      val actor = lastSender
      actor ! SubscribeToStreamCompleted(0, None)
      actor ! StreamEventAppeared(event0)
      actor ! StreamEventAppeared(event1)
      client expectMsg LiveProcessingStart
      client expectMsg event0.event
      client expectMsg event1.event
      closeable.close()
      client expectMsg Close
    }

    "subscribe to stream from" in new StreamSubscriptionScope {
      val closeable = connection.subscribeToStreamFrom(streamId.streamId, observer, null, false, null)
      expectMsg(ReadStreamEvents(streamId, EventNumber(0)))
      val actor = lastSender
      actor ! readEventsCompleted(event0, event1)
      expectMsg(SubscribeTo(streamId))
      actor ! SubscribeToStreamCompleted(0, None)
      actor ! StreamEventAppeared(event2)
      client expectMsg event0.event
      client expectMsg event1.event
      client expectMsg LiveProcessingStart
      client expectMsg event2.event
      actor ! Failure(error)
      client expectMsg error
      client expectMsg Close
    }

    "subscribe to all" in new SubscriptionScope[IndexedEvent] {
      val closeable = connection.subscribeToAll(observer, false, null)
      expectMsg(SubscribeTo(EventStream.All))
      val actor = lastSender
      actor ! SubscribeToAllCompleted(0)
      actor ! StreamEventAppeared(event0)
      actor ! StreamEventAppeared(event1)
      client expectMsg LiveProcessingStart
      client expectMsg event0
      client expectMsg event1
      closeable.close()
      client expectMsg Close
    }

    "subscribe to all from" in new SubscriptionScope[IndexedEvent] {
      val closeable = connection.subscribeToAllFrom(observer, Position.Exact(0), false, null)
      expectMsg(ReadAllEvents(Position(0)))
      val actor = lastSender
      actor ! ReadAllEventsCompleted(List(event0, event1), Position.Exact(0), Position.Exact(2), ReadDirection.forward)
      expectMsg(ReadAllEvents(Position(2)))
      actor ! ReadAllEventsCompleted(Nil, Position.Exact(2), Position.Exact(2), ReadDirection.forward)
      expectMsg(SubscribeTo(EventStream.All))
      actor ! SubscribeToAllCompleted(0)
      actor ! StreamEventAppeared(event2)
      client expectMsg event1
      client expectMsg LiveProcessingStart
      client expectMsg event2
      actor ! Failure(error)
      client expectMsg error
      client expectMsg Close
    }

    "start transaction" in new TestScope {
      for {
        stream <- streams
        version <- existingVersions
        uc <- userCredentials
      } {
        connection.startTransaction(stream, version, uc)
        val msg = TransactionStart(
          streamId = EventStream.Id(stream),
          expectedVersion = Option(version) getOrElse ExpectedVersion.Any
        )
        expect(msg, uc)
      }
    }

    "continue transaction" in new TestScope {
      for {
        uc <- userCredentials
      } connection.continueTransaction(1234, uc)
      expectNoMsg(1.second)
    }
  }

  private trait TestScope extends ActorScope {
    val underlying = new eventstore.EsConnection(testActor, system)
    val connection: EsConnection = new EsConnectionImpl(underlying, Settings.Default)

    def expect(x: Out, userCredentials: UserCredentials) = Option(userCredentials) match {
      case Some(uc) => expectMsg(x.withCredentials(uc))
      case None     => expectMsg(x)
    }
  }

  private trait SubscriptionScope[T] extends TestScope {
    val streamId = EventStream.Id("streamId")
    val client = TestProbe()

    val observer = new SubscriptionObserver[T] {
      def onEvent(event: T, subscription: Closeable) = client.ref ! event

      def onError(e: Throwable) = client.ref ! e

      def onClose() = client.ref ! Close

      def onLiveProcessingStart(subscription: Closeable) = client.ref ! LiveProcessingStart
    }

    def newEvent(x: Int) = IndexedEvent(EventRecord(streamId, EventNumber.Exact(x), EventData("event-type")), Position.Exact(x.toLong))

    val error = new RuntimeException("test")

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)

    case object Close
    case object LiveProcessingStart
  }

  private trait StreamSubscriptionScope extends SubscriptionScope[Event] {
    def readEventsCompleted(xs: IndexedEvent*) = ReadStreamEventsCompleted(
      xs.map(_.event).toList,
      EventNumber(0),
      EventNumber.Exact(2),
      endOfStream = true,
      0,
      ReadDirection.forward
    )
  }
}