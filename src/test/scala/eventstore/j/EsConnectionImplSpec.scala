package eventstore
package j

import scala.collection.JavaConverters._

class EsConnectionImplSpec extends util.ActorSpec {

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

  val userCredentials = List(UserCredentials.defaultAdmin, UserCredentials("login", "password"), null)

  "EsConnection" should {
    "write events" in new TestScope {
      for {
        stream <- streams
        version <- versions
        uc <- userCredentials
      } {
        connection.writeEvents(stream, version, events, uc)
        expect(WriteEvents(
          streamId = EventStream(stream),
          events = events.asScala.toList,
          expectedVersion = Option(version) getOrElse ExpectedVersion.Any), uc)
      }
    }

    "delete stream" in new TestScope {
      for {
        stream <- streams
        version <- existingVersions
        uc <- userCredentials
      } {
        connection.deleteStream(stream, version, uc)
        expect(DeleteStream(
          streamId = EventStream(stream),
          expectedVersion = Option(version) getOrElse ExpectedVersion.Any), uc)
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
          streamId = EventStream(stream),
          eventNumber = Option(number) getOrElse EventNumber.Last,
          resolveLinkTos = resolveLinkTos), uc)
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
          streamId = EventStream(stream),
          fromNumber = Option(number) getOrElse EventNumber.First,
          maxCount = count,
          direction = ReadDirection.Forward,
          resolveLinkTos = resolveLinkTos), uc)
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
          streamId = EventStream(stream),
          fromNumber = Option(number) getOrElse EventNumber.Last,
          maxCount = count,
          direction = ReadDirection.Backward,
          resolveLinkTos = resolveLinkTos), uc)
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
          direction = ReadDirection.Forward,
          resolveLinkTos = resolveLinkTos), uc)
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
          direction = ReadDirection.Backward,
          resolveLinkTos = resolveLinkTos), uc)
      }
    }
  }

  trait TestScope extends ActorScope {
    val underlying = new eventstore.EsConnection(testActor, system, None)
    val connection: EsConnection = new EsConnectionImpl(underlying)

    def expect(x: Out, userCredentials: UserCredentials) = Option(userCredentials) match {
      case Some(uc) => expectMsg(x.withCredentials(uc))
      case None     => expectMsg(x)
    }
  }

}
