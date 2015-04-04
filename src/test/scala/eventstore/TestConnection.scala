package eventstore

import akka.actor.Status.Failure
import akka.testkit._
import eventstore.ReadDirection._
import eventstore.tcp.ConnectionActor
import org.joda.time.DateTime
import scala.concurrent.duration._

abstract class TestConnection extends util.ActorSpec {

  abstract class TestConnectionScope extends ActorScope {
    val streamId = newStreamId
    val date = DateTime.now

    val streamMetadata = ByteString(TestConnection.this.getClass.getSimpleName)
    val actor = TestActorRef(ConnectionActor.props())

    def deleteStream(expVer: ExpectedVersion.Existing = ExpectedVersion.Any, hard: Boolean = true): Unit = {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, hard = hard, expectedVersion = expVer))(probe.ref)
      probe.expectMsgType[DeleteStreamCompleted].position must beSome
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
      val completed = testKit.expectMsgType[WriteEventsCompleted]
      completed.position must beSome
      val range = completed.numbersRange
      if (expectedVersion == ExpectedVersion.NoStream) events match {
        case Nil => range must beNone
        case xs  => range must beSome(EventNumber.First to EventNumber.Exact(xs.size - 1))
      }
      range
    }

    def appendEventToCreateStream(): EventData = {
      val event = newEventData.copy(eventType = "first event")
      val range = writeEventsCompleted(List(event), ExpectedVersion.NoStream, testKit = TestProbe())
      range must beSome(EventNumber.Range(EventNumber.First))
      event
    }

    def append(x: EventData): EventRecord = {
      EventRecord(streamId, writeEventsCompleted(List(x), testKit = TestProbe()).get.start, x, Some(date))
    }

    def linkedAndLink(): (EventRecord, EventRecord) = {
      val linked = append(newEventData.copy(eventType = "linked"))
      append(newEventData)
      val link = append(linked.link())
      (linked, link)
    }

    def appendMany(size: Int = 10, testKit: TestKitBase = this): List[EventData] = {
      val events = (1 to size).map(_ => newEventData).toList
      actor.!(WriteEvents(streamId, events, ExpectedVersion.Any))(testKit.testActor)
      testKit.expectMsgType[WriteEventsCompleted]
      events
    }

    def readEventCompleted(eventNumber: EventNumber, resolveLinkTos: Boolean = false) = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = resolveLinkTos)
      val event = expectMsgType[ReadEventCompleted].fixDate.event
      event.streamId mustEqual streamId
      if (!resolveLinkTos) event must beAnInstanceOf[EventRecord]
      if (eventNumber != EventNumber.Last) event.number mustEqual eventNumber
      event
    }

    def readStreamEventsCompleted(fromEventNumber: EventNumber, maxCount: Int, resolveLinkTos: Boolean = false)(implicit direction: ReadDirection) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadStreamEventsCompleted].fixDate
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

    def expectEsException(): Unit = {
      expectMsgPF() { case Failure(e: EsException) => throw e }
    }

    def readStreamEventsFailed(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection): Unit = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      expectEsException()
    }

    def readStreamEventsFailed(implicit direction: ReadDirection): Unit = {
      readStreamEventsFailed(EventNumber(direction), Settings.Default.readBatchSize)
    }

    def streamEvents(implicit direction: ReadDirection = Forward): Stream[EventData] =
      streamEventRecords(direction).map(_.data)

    def streamEventRecords(implicit direction: ReadDirection = Forward): Stream[Event] = {
      def loop(position: EventNumber): Stream[Event] = {
        val result = readStreamEventsCompleted(position, Settings.Default.readBatchSize)
        val resolvedIndexedEvents = result.events
        if (resolvedIndexedEvents.isEmpty || result.endOfStream) resolvedIndexedEvents.toStream
        else resolvedIndexedEvents.toStream #::: loop(result.nextEventNumber)
      }
      loop(EventNumber(direction))
    }

    def expectStreamEventAppeared(testKit: TestKitBase = this) = {
      val event = testKit.expectMsgType[StreamEventAppeared].fixDate.event
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
      val result = expectMsgType[ReadAllEventsCompleted](10.seconds).fixDate
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
      expectEsException()
    }

    def readAllEvents(position: Position, maxCount: Int)(implicit direction: ReadDirection): List[Event] =
      readAllEventsCompleted(position = position, maxCount = maxCount).events.map(_.event)

    def allStreamsEvents(maxCount: Int = Settings.Default.readBatchSize)(implicit direction: ReadDirection): Stream[IndexedEvent] = {
      def loop(position: Position): Stream[IndexedEvent] = {
        val result = readAllEventsCompleted(position, maxCount)
        val events = result.events
        if (events.isEmpty) Stream()
        else events.toStream #::: loop(result.nextPosition)
      }
      loop(Position(direction))
    }

    def allStreamsEventsData(maxCount: Int = Settings.Default.readBatchSize)(implicit direction: ReadDirection) =
      allStreamsEvents(maxCount).map(_.event.data)

    def newStreamId: EventStream.Plain = EventStream.Plain(TestConnection.this.getClass.getSimpleName + "-" + randomUuid.toString)

    implicit class RichEventRecord(self: EventRecord) {
      def fixDate: EventRecord = self.copy(created = Some(date))
    }

    implicit class RichEvent(self: Event) {
      def fixDate: Event = self match {
        case x: EventRecord   => x.fixDate
        case x: ResolvedEvent => x.copy(linkedEvent = x.linkedEvent.fixDate, linkEvent = x.linkEvent.fixDate)
      }
    }

    implicit class RichIndexedEvent(self: IndexedEvent) {
      def fixDate: IndexedEvent = self.copy(event = self.event.fixDate)
    }

    implicit class RichReadAllEventsCompleted(self: ReadAllEventsCompleted) {
      def fixDate: ReadAllEventsCompleted = self.copy(events = self.events.map(_.fixDate))
    }

    implicit class RichReadStreamEventsCompleted(self: ReadStreamEventsCompleted) {
      def fixDate: ReadStreamEventsCompleted = self.copy(events = self.events.map(_.fixDate))
    }

    implicit class RichStreamEventAppeared(self: StreamEventAppeared) {
      def fixDate: StreamEventAppeared = self.copy(event = self.event.fixDate)
    }

    implicit class RichReadEventCompleted(self: ReadEventCompleted) {
      def fixDate: ReadEventCompleted = self.copy(event = self.event.fixDate)
    }
  }
}

