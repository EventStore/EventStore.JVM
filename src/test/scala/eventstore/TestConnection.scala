package eventstore

import ReadDirection._
import akka.actor.Status.Failure
import akka.testkit._
import scala.concurrent.duration._
import tcp.ConnectionActor

abstract class TestConnection extends util.ActorSpec {

  abstract class TestConnectionScope extends ActorScope {
    val streamId = newStreamId

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(ConnectionActor.props())

    def deleteStream(expVer: ExpectedVersion.Existing = ExpectedVersion.Any) {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, expVer, hardDelete = true))(probe.ref)
      probe.expectMsg(DeleteStreamCompleted)
    }

    def newEventData: EventData = EventData(
      "test",
      data = Content("""{"data":"data"}"""),
      metadata = Content("""{"metadata":"metadata"}"""))

    def writeEventsCompleted(
      events: List[EventData],
      expectedVersion: ExpectedVersion = ExpectedVersion.Any,
      streamId: EventStream.Id = streamId,
      testKit: TestKitBase = this): Option[EventNumber.Range] = {
      actor.!(WriteEvents(streamId, events, expectedVersion))(testKit.testActor)
      val range = testKit.expectMsgType[WriteEventsCompleted].numbersRange
      if (expectedVersion == ExpectedVersion.NoStream) events match {
        case Nil => range must beNone
        case xs  => range must beSome(EventNumber.First to EventNumber(xs.size - 1))
      }
      range
    }

    def appendEventToCreateStream(): EventData = {
      val event = newEventData.copy(eventType = "first event")
      val range = writeEventsCompleted(List(event), ExpectedVersion.NoStream, testKit = TestProbe())
      range must beSome(EventNumber.Range(EventNumber.First))
      event
    }

    def append(x: EventData): EventRecord = EventRecord(
      streamId,
      writeEventsCompleted(List(x), testKit = TestProbe()).get.start, x)

    def linkedAndLink(): (EventRecord, EventRecord) = {
      val linked = append(newEventData.copy(eventType = "linked"))
      append(newEventData)
      val link = append(linked.link())
      (linked, link)
    }

    def appendMany(size: Int = 10, testKit: TestKitBase = this): List[EventData] = {
      val events = (1 to size).map(_ => newEventData).toList

      def loop(n: Int) {
        actor.!(WriteEvents(streamId, events, ExpectedVersion.Any))(testKit.testActor)
        testKit.expectMsgPF(10.seconds) {
          case WriteEventsCompleted(_)                                  => true
          case Failure(EsException(EsError.PrepareTimeout, _)) if n < 3 => loop(n + 1) // TODO
        }
      }

      loop(0)
      events
    }

    def readEventCompleted(eventNumber: EventNumber, resolveLinkTos: Boolean = false) = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = resolveLinkTos)
      val event = expectMsgType[ReadEventCompleted].event
      event.streamId mustEqual streamId
      if (!resolveLinkTos) event must beAnInstanceOf[EventRecord]
      if (eventNumber != EventNumber.Last) event.number mustEqual eventNumber
      event
    }

    def readStreamEventsCompleted(fromEventNumber: EventNumber, maxCount: Int, resolveLinkTos: Boolean = false)(implicit direction: ReadDirection) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadStreamEventsCompleted]
      result.direction mustEqual direction

      val events = result.events
      events.size must beLessThanOrEqualTo(maxCount)
      mustBeSorted(events)
      events.foreach {
        x =>
          if (!resolveLinkTos) x must beAnInstanceOf[EventRecord]

          def verify(x: EventRecord) = {
            x.streamId mustEqual streamId
            val number = x.number
            number must beLessThanOrEqualTo(result.lastEventNumber)

            direction match {
              case Forward  => x.number must beGreaterThanOrEqualTo(fromEventNumber)
              case Backward => x.number must beLessThanOrEqualTo(fromEventNumber)
            }
          }
          verify(x.record)
      }
      result
    }

    def readStreamEvents(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection) = {
      val result = readStreamEventsCompleted(fromEventNumber = fromEventNumber, maxCount = maxCount)
      result.events.map(_.data)
    }

    def expectException(): EsError = expectMsgPF() {
      case Failure(e: EsException) => e.reason
    }

    def readStreamEventsFailed(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection): EsError = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      expectException()
    }

    def readStreamEventsFailed(implicit direction: ReadDirection): EsError =
      readStreamEventsFailed(EventNumber.start(direction), 500)

    def streamEvents(implicit direction: ReadDirection = Forward): Stream[EventData] =
      streamEventRecords(direction).map(_.data)

    def streamEventRecords(implicit direction: ReadDirection = Forward): Stream[Event] = {
      def loop(position: EventNumber): Stream[Event] = {
        val result = readStreamEventsCompleted(position, 500)
        val resolvedIndexedEvents = result.events
        if (resolvedIndexedEvents.isEmpty || result.endOfStream) resolvedIndexedEvents.toStream
        else resolvedIndexedEvents.toStream #::: loop(result.nextEventNumber)
      }
      loop(EventNumber.start(direction))
    }

    def expectStreamEventAppeared(testKit: TestKitBase = this) = {
      val event = testKit.expectMsgType[StreamEventAppeared].event
      event.event.streamId mustEqual streamId
      event
    }

    def mustBeSorted[T](xs: List[T])(implicit direction: ReadDirection, ordering: Ordering[T]) {
      xs.map {
        case ResolvedEvent(_, link) => link.asInstanceOf[T]
        case x                      => x
      } must beSorted(direction match {
        case Forward  => ordering
        case Backward => ordering.reverse
      })
    }

    def readAllEventsCompleted(position: Position, maxCount: Int, resolveLinkTos: Boolean = false)(implicit direction: ReadDirection) = {
      actor ! ReadAllEvents(position, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadAllEventsCompleted](10.seconds)
      result.direction mustEqual direction

      val events = result.events
      events.size must beLessThanOrEqualTo(maxCount)
      events.foreach {
        x =>
          if (!resolveLinkTos) x.event must beAnInstanceOf[EventRecord]
          direction match {
            case Forward  => x.position must beGreaterThanOrEqualTo(position)
            case Backward => x.position must beLessThanOrEqualTo(position)
          }
      }
      mustBeSorted(events)

      result
    }

    def readAllEventsFailed(position: Position, maxCount: Int)(implicit direction: ReadDirection) = {
      actor ! ReadAllEvents(position, maxCount, direction)
      expectException()
    }

    def readAllEvents(position: Position, maxCount: Int)(implicit direction: ReadDirection): List[Event] =
      readAllEventsCompleted(position = position, maxCount = maxCount).events.map(_.event)

    def allStreamsEvents(maxCount: Int = 500)(implicit direction: ReadDirection): Stream[IndexedEvent] = {
      def loop(position: Position): Stream[IndexedEvent] = {
        val result = readAllEventsCompleted(position, maxCount)
        val events = result.events
        if (events.isEmpty) Stream()
        else events.toStream #::: loop(result.nextPosition)
      }
      loop(Position.start(direction))
    }

    def allStreamsEventsData(maxCount: Int = 500)(implicit direction: ReadDirection) =
      allStreamsEvents(maxCount).map(_.event.data)

    def newStreamId = EventStream(getClass.getEnclosingClass.getSimpleName + "-" + randomUuid.toString)
  }
}

