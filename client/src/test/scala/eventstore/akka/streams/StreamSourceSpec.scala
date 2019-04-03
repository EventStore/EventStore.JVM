package eventstore
package akka
package streams

import _root_.akka.NotUsed
import _root_.akka.actor.Status
import _root_.akka.actor.Status.Failure
import _root_.akka.stream.scaladsl._
import ReadDirection.Forward
import eventstore.akka.streams.SourceStageLogic.ConnectionTerminated

class StreamSourceSpec extends SourceSpec {

  "StreamSource" should {

    "read events from given position" in new SourceScope {
      connection expectMsg readEvents(123)

      override def eventNumber = Some(EventNumber(123))
    }

    "read events from start if no position given" in new SourceScope {
      connection expectMsg readEvents(0)
    }

    "read events record event number" in new SourceScope {

      connection expectMsg readEvents(0)

      val e1 = EventRecord(EventStream.Id("e-a1"), EventNumber.First, EventData("a", randomUuid))
      val r1 = ResolvedEvent(e1.record, EventRecord(streamId, EventNumber.First, e1.link(randomUuid)))

      val e2 = EventRecord(EventStream.Id("e-b1"), EventNumber.First, EventData("b", randomUuid))
      val r2 = ResolvedEvent(e2.record, EventRecord(streamId, EventNumber.Exact(1), e2.link(randomUuid)))

      connection reply readCompleted(2, false, r1, r2)

      expectEvent(r1)
      expectEvent(r2)

    }

    "subscribe if last position given" in new SourceScope {
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection.expectNoMessage()
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event0)
      connection reply streamEventAppeared(event2)
      expectEvent(event1)
      expectEvent(event2)

      override def eventNumber = Some(EventNumber.Last)
    }

    "ignore read events with event number out of interest" in new SourceScope {
      connection expectMsg readEvents(0)

      connection reply readCompleted(3, false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply readCompleted(5, false, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readEvents(5)

      connection reply readCompleted(5, false, event0, event1, event2, event3, event4)

      expectNoEvent()
      connection expectMsg readEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new SourceScope {
      connection expectMsg readEvents(1)

      connection reply readCompleted(3, false, event0, event1, event2)
      expectEvent(event2)
      expectNoEvent()

      connection expectMsg readEvents(3)

      override def eventNumber = Some(EventNumber(1))
    }

    "read events until none left and subscribe to new ones" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(2, false, event1)

      expectEvent(event1)

      connection expectMsg readEvents(2)
      connection reply readCompleted(2, endOfStream = true)

      connection expectMsg subscribeTo
    }

    "subscribe to new events if nothing to read" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
    }

    "catch events that appear in between reading and subscribing" in new SourceScope {
      connection expectMsg readEvents(0)

      connection reply readCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)
      connection reply readCompleted(2, endOfStream = true)

      expectNoEvent()
      connection expectMsg subscribeTo

      connection reply subscribeCompleted(4)

      connection expectMsg readEvents(1)

      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      connection reply streamEventAppeared(event4)
      expectNoEvent()

      connection reply readCompleted(3, false, event2)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply streamEventAppeared(event5)
      connection reply streamEventAppeared(event6)
      expectNoEvent()

      connection reply readCompleted(6, false, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectEvent(event5)
      expectEvent(event6)

      connection reply streamEventAppeared(event5)
      connection reply streamEventAppeared(event6)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new SourceScope {
      val position = 0L
      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoEvent()

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in
      new SourceScope {
        val position = 1L
        connection expectMsg readEvents(position)

        connection reply readCompleted(position, endOfStream = true)

        connection expectMsg subscribeTo
        expectNoEvent()

        connection reply subscribeCompleted(1)

        expectNoActivity()

        override def eventNumber = Some(EventNumber(1))
      }

    "forward events while subscribed" in new SourceScope {
      val position = 0L
      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoEvent()

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      connection reply streamEventAppeared(event1)
      expectEvent(event1)

      expectNoEvent()

      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new SourceScope {
      connection expectMsg readEvents(1)
      connection reply readCompleted(1, endOfStream = true)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(3)

      connection expectMsg readEvents(1)
      connection reply readCompleted(1, endOfStream = true)

      connection reply streamEventAppeared(event0)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      expectEvent(event2)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event3)
      expectEvent(event3)
      connection reply streamEventAppeared(event5)
      expectEvent(event5)
      connection reply streamEventAppeared(event4)
      expectNoEvent()

      override def eventNumber = Some(EventNumber(1))
    }

    "fail source if connection stopped" in new SourceScope {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectError(ConnectionTerminated)
    }

    "stop source if error while reading" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()
    }

    "stop source if error while subscribing" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()

      override def eventNumber = Some(EventNumber(0))
    }

    "stop source if error while catching up" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()

      override def eventNumber = Some(EventNumber(0))
    }

    "stop source if error while live processing" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()

      override def eventNumber = Some(EventNumber(0))
    }

    "resubscribe correctly if unexpectedly unsubscribed" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection expectMsg readEvents(0)
      connection reply readCompleted(3, endOfStream = false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)
      connection reply Unsubscribed
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(10)
      connection expectMsg readEvents(2)
    }

    "resubscribe correctly if unexpectedly unsubscribed and last pushed is not set" in new SourceScope {

      val testEvent1 = newEvent(1337)
      val testEvent2 = newEvent(1338)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1336)
      connection.expectNoMessage()
      connection reply streamEventAppeared(testEvent1)
      expectEvent(testEvent1)
      connection reply Unsubscribed
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1339)
      connection expectMsg readEvents(1337)
      connection reply readCompleted(1339, endOfStream = true, testEvent1, testEvent2)
      expectEvent(testEvent2)

      override def eventNumber = Some(EventNumber.Last)
    }

    "resubscribe correctly if unexpectedly unsubscribed while catching up" in new SourceScope {

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(2)
      connection expectMsg readEvents(0)
      connection reply readCompleted(2, endOfStream = false, event0, event1)
      connection expectMsg readEvents(2)

      expectEvent(event0)
      expectEvent(event1)

      connection reply Unsubscribed
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(3)
      connection expectMsg readEvents(1)
      connection reply readCompleted(4, endOfStream = false, event1, event2, event3)

      expectEvent(event2)
      expectEvent(event3)

    }

    "handle unexpected resubscribe while subscribed" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(1, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection reply streamEventAppeared(event1)
      connection reply subscribeCompleted(3)
      connection expectMsg readEvents(1)
      connection reply streamEventAppeared(event4)
      connection reply readCompleted(4, false, event1, event2, event3)

      expectEvent(event1)
      expectEvent(event2)
      expectEvent(event3)
      expectEvent(event4)

      override def eventNumber = Some(EventNumber(0))
    }

    "handle unexpected resubscribe while catching up" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(2)
      connection expectMsg readEvents(0)
      connection reply subscribeCompleted(3)
      connection reply streamEventAppeared(event4)
      connection reply readCompleted(3, false, event0, event1, event2)
      connection expectMsg readEvents(3)
      connection reply readCompleted(4, false, event3)

      expectEvent(event1)
      expectEvent(event2)
      expectEvent(event3)
      expectEvent(event4)

      override def eventNumber = Some(EventNumber(0))
    }

    "temporarily unsubscribe when buffer is full and ignore appearing events" in new SourceScope {
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      connection expectMsg Unsubscribe
      connection reply streamEventAppeared(event4)
      connection reply streamEventAppeared(event5)
      connection reply Unsubscribed
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(5)
      connection expectMsg readEvents(3)
      connection reply readCompleted(6, false, event3, event4, event5)
      expectEvent(event3)
      expectEvent(event4)
      expectEvent(event5)

      override def eventNumber = Some(EventNumber.Last)
    }

    "temporarily halt reading when buffer is full" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(3, false, event0, event1, event2)
      connection expectNoMessage ()
      expectEvent(event0)
      expectEvent(event1)
      connection expectMsg readEvents(3)
      connection reply readCompleted(4, endOfStream = true, event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "use credentials if given" in new SourceScope {
      connection expectMsg readEvents(0).withCredentials(credentials.get)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo.withCredentials(credentials.get)

      override def credentials = Some(UserCredentials("login", "password"))
    }

    "subscribe to non-existing stream" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply Status.Failure(StreamNotFoundException(streamId))
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection expectMsg readEvents(0)
    }

    "subscribe to non-existing stream if last number passed" in new SourceScope {
      connection expectMsg readEvents(2)
      connection reply Status.Failure(StreamNotFoundException(streamId))
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(2)
      connection reply streamEventAppeared(event3)
      expectEvent(event3)

      override def eventNumber = Some(EventNumber.Exact(2))
    }

  }

  "StreamSource finite" should {

    "stop immediately if last number passed" in new FiniteSourceScope {
      connection.expectNoMessage()
      expectComplete()

      override def eventNumber = Some(EventNumber.Last)
    }

    "stop when no more events left" in new FiniteSourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      expectComplete()
    }

    "stop when retrieved last event" in new FiniteSourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(2, false, event0, event1)
      expectEvent(event0)
      expectEvent(event1)
      connection expectMsg readEvents(2)
      connection reply readCompleted(2, endOfStream = true)
      expectComplete()
    }

    "subscribe to non-existing stream" in new FiniteSourceScope {
      connection expectMsg readEvents(0)
      connection reply Status.Failure(StreamNotFoundException(streamId))
      expectComplete()
    }

    "subscribe to non-existing stream if last number passed" in new FiniteSourceScope {
      connection expectMsg readEvents(5)
      connection reply Status.Failure(StreamNotFoundException(streamId))
      expectComplete()

      override def eventNumber = Some(EventNumber.Exact(5))
    }
  }

  private trait SourceScope extends AbstractSourceScope[Event] {

    lazy val streamId = EventStream.Id(StreamSourceSpec.this.getClass.getSimpleName + "-" + randomUuid.toString)
    def eventNumber: Option[EventNumber] = None

    def createSource(): Source[Event, NotUsed] =
      Source.fromGraph(new StreamSourceStage(
        connection.ref,
        streamId,
        eventNumber,
        credentials,
        Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos),
        infinite
      ))

    def newEvent(number: Long): Event =
      EventRecord(streamId, EventNumber.Exact(number), TestData.eventData)

    def readEvents(x: Long) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readCompleted(next: Long, endOfStream: Boolean, events: Event*) = ReadStreamEventsCompleted(
      events = events.toList,
      nextEventNumber = EventNumber(next),
      lastEventNumber = EventNumber.Exact(1L),
      endOfStream = endOfStream,
      lastCommitPosition = next.toLong,
      direction = Forward
    )

    def subscribeCompleted(x: Long) = SubscribeToStreamCompleted(x.toLong, Some(EventNumber.Exact(x)))
    def streamEventAppeared(x: Event) = StreamEventAppeared(IndexedEvent(x, Position.Exact(x.number.value)))

  }

  private trait FiniteSourceScope extends SourceScope {
    override def infinite = false
  }
}
