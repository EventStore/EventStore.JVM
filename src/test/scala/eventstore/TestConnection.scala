package eventstore

import akka.testkit._
import scala.concurrent.duration._
import tcp.ConnectionActor
import ReadDirection._

/**
 * @author Yaroslav Klymko
 */
abstract class TestConnection extends util.ActorSpec {

  abstract class TestConnectionScope extends ActorScope {
    val streamId = newStreamId

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(Settings()))

    def deleteStream(expVer: ExpectedVersion.Existing = ExpectedVersion.Any) {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, expVer))(probe.ref)
      probe.expectMsg(DeleteStreamSucceed)
    }

    def newEventData: EventData = EventData(
      newUuid, "test",
      //      dataContentType = ContentType.Json,
      data = ByteString("""{"data":"data"}"""),
      //      metadataContentType = ContentType.Json,
      metadata = ByteString("""{"metadata":"metadata"}"""))

    def writeEventsSucceed(
      events: Seq[EventData],
      expectedVersion: ExpectedVersion = ExpectedVersion.Any,
      streamId: EventStream.Id = streamId,
      testKit: TestKitBase = this): EventNumber.Exact = {
      actor.!(WriteEvents(streamId, events, expectedVersion))(testKit.testActor)
      val firstEventNumber = testKit.expectMsgType[WriteEventsSucceed].firstEventNumber
      if (expectedVersion == ExpectedVersion.NoStream) firstEventNumber mustEqual EventNumber.First
      firstEventNumber
    }

    def appendEventToCreateStream(): EventData = {
      val event = newEventData.copy(eventType = "first event")
      writeEventsSucceed(Seq(event), ExpectedVersion.NoStream, testKit = TestProbe()) mustEqual EventNumber.First
      event
    }

    def append(x: EventData): EventRecord = EventRecord(streamId, writeEventsSucceed(Seq(x), testKit = TestProbe()), x)

    def linkedAndLink(): (EventRecord, EventRecord) = {
      val linked = append(newEventData.copy(eventType = "linked"))
      append(newEventData)
      val link = append(linked.link(newUuid))
      (linked, link)
    }

    def appendMany(size: Int = 10, testKit: TestKitBase = this): Seq[EventData] = {
      val events = (1 to size).map(_ => newEventData)

      def loop(n: Int) {
        actor.!(WriteEvents(streamId, events, ExpectedVersion.Any))(testKit.testActor)
        testKit.expectMsgPF(10.seconds) {
          case WriteEventsSucceed(_)                                         => true
          case WriteEventsFailed(OperationFailed.PrepareTimeout, _) if n < 3 => loop(n + 1) // TODO
        }
      }

      loop(0)
      events
    }

    def readEventSucceed(eventNumber: EventNumber, resolveLinkTos: Boolean = false) = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = resolveLinkTos)
      val event = expectMsgType[ReadEventSucceed].event
      event.streamId mustEqual streamId
      if (!resolveLinkTos) event must beAnInstanceOf[EventRecord]
      if (eventNumber != EventNumber.Last) event.number mustEqual eventNumber
      event
    }

    def readStreamEventsSucceed(fromEventNumber: EventNumber, maxCount: Int, resolveLinkTos: Boolean = false)(implicit direction: ReadDirection.Value) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadStreamEventsSucceed]
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

    def readStreamEvents(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      val result = readStreamEventsSucceed(fromEventNumber = fromEventNumber, maxCount = maxCount)
      result.events.map(_.data)
    }

    def readStreamEventsFailed(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection.Value): ReadStreamEventsFailed = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val failed = expectMsgType[ReadStreamEventsFailed]
      failed.direction mustEqual direction
      failed
    }

    def readStreamEventsFailed(implicit direction: ReadDirection.Value): ReadStreamEventsFailed =
      readStreamEventsFailed(EventNumber.start(direction), 500)

    def streamEvents(implicit direction: ReadDirection.Value = Forward): Stream[EventData] =
      streamEventRecords(direction).map(_.data)

    def streamEventRecords(implicit direction: ReadDirection.Value = Forward): Stream[Event] = {
      def loop(position: EventNumber): Stream[Event] = {
        val result = readStreamEventsSucceed(position, 500)
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

    def mustBeSorted[T](xs: Seq[T])(implicit direction: ReadDirection.Value, ordering: Ordering[T]) {
      xs.map {
        case ResolvedEvent(_, link) => link.asInstanceOf[T]
        case x                      => x
      } must beSorted(direction match {
        case Forward  => ordering
        case Backward => ordering.reverse
      })
    }

    def readAllEventsSucceed(position: Position, maxCount: Int, resolveLinkTos: Boolean = false)(implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadAllEventsSucceed](10.seconds)
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

    def readAllEventsFailed(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction)
      val failed = expectMsgType[ReadAllEventsFailed]
      failed.direction mustEqual direction
      failed.position mustEqual position
      failed
    }

    def readAllEvents(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value): Seq[Event] =
      readAllEventsSucceed(position = position, maxCount = maxCount).events.map(_.event)

    def allStreamsEvents(maxCount: Int = 500)(implicit direction: ReadDirection.Value): Stream[IndexedEvent] = {
      def loop(position: Position): Stream[IndexedEvent] = {
        val result = readAllEventsSucceed(position, maxCount)
        val events = result.events
        if (events.isEmpty) Stream()
        else events.toStream #::: loop(result.nextPosition)
      }
      loop(Position.start(direction))
    }

    def allStreamsEventsData(maxCount: Int = 500)(implicit direction: ReadDirection.Value) =
      allStreamsEvents(maxCount).map(_.event.data)

    def newStreamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

  }
}

