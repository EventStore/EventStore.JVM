package eventstore
package akka
package streams

import _root_.akka.NotUsed
import _root_.akka.actor.Status.Failure
import _root_.akka.stream.scaladsl._
import eventstore.ReadDirection.Forward

class AllStreamsSourceSpec extends SourceSpec {

  "AllStreamsSource" should {

    "read events from given position" in new SourceScope {
      connection expectMsg readEvents(123)

      override def position = Some(Position(123))
    }

    "read events from start if no position given" in new SourceScope {
      connection expectMsg readEvents(0)
    }

    "subscribe if last position given" in new SourceScope {
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection.expectNoMessage(duration)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event0)
      connection reply StreamEventAppeared(event2)
      expectEvent(event1)
      expectEvent(event2)

      override def position = Some(Position.Last)
    }

    "ignore read events with position out of interest" in new SourceScope {
      connection expectMsg readEvents(0)

      connection reply readCompleted(0, 3, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply readCompleted(3, 5, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readEvents(5)

      connection reply readCompleted(3, 5, event0, event1, event2, event3, event4)

      expectNoEvent()
      connection expectMsg readEvents(5)
    }

    "ignore read events with position out of interest when start position is given" in new SourceScope {
      connection expectMsg readEvents(1)

      connection reply readCompleted(0, 3, event0, event1, event2)
      expectEvent(event2)
      expectNoEvent()

      connection expectMsg readEvents(3)

      override def position = Some(Position(1))
    }

    "read events until none left and subscribe to new ones" in new SourceScope {
      connection expectMsg readEvents(0)
      val nextPosition = 2L
      connection reply readCompleted(1, nextPosition, event1)

      expectEvent(event1)

      connection expectMsg readEvents(nextPosition)
      connection reply readCompleted(nextPosition, nextPosition)

      connection.expectMsg(subscribeTo)
    }

    "subscribe to new events if nothing to read" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection.expectMsg(subscribeTo)

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
    }

    "catch events that appear in between reading and subscribing" in new SourceScope {
      connection expectMsg readEvents(0)

      connection reply readCompleted(0, 2, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)
      connection reply readCompleted(2, 2)

      expectNoEvent()
      connection.expectMsg(subscribeTo)

      connection reply subscribeCompleted(4)

      connection expectMsg readEvents(1)

      connection reply StreamEventAppeared(event2)
      connection reply StreamEventAppeared(event3)
      connection reply StreamEventAppeared(event4)
      expectNoEvent()

      connection reply readCompleted(1, 3, event1, event2)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply StreamEventAppeared(event5)
      connection reply StreamEventAppeared(event6)
      expectNoEvent()

      connection reply readCompleted(3, 6, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectEvent(event5)
      expectEvent(event6)

      connection reply StreamEventAppeared(event5)
      connection reply StreamEventAppeared(event6)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)

      connection.expectMsg(subscribeTo)
      expectNoEvent()

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in
      new SourceScope {
        connection expectMsg readEvents(1)

        connection reply readCompleted(1, 1)

        connection.expectMsg(subscribeTo)
        expectNoEvent()

        connection reply subscribeCompleted(1)

        expectNoActivity()

        override def position = Some(Position(1))
      }

    "forward events while subscribed" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)

      connection.expectMsg(subscribeTo)
      expectNoEvent()

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)

      connection reply StreamEventAppeared(event1)
      expectEvent(event1)

      expectNoEvent()
      //      expectNoMessage(duration)

      connection reply StreamEventAppeared(event2)
      connection reply StreamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new SourceScope {
      connection expectMsg readEvents(1)
      connection reply readCompleted(1, 1)

      connection.expectMsg(subscribeTo)
      connection reply subscribeCompleted(2)

      connection expectMsg readEvents(1)
      connection reply readCompleted(1, 1)

      connection reply StreamEventAppeared(event0)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event2)
      expectEvent(event2)
      connection reply StreamEventAppeared(event2)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event3)
      expectEvent(event3)
      connection reply StreamEventAppeared(event5)
      expectEvent(event5)
      connection reply StreamEventAppeared(event4)
      expectNoEvent()

      override def position = Some(Position(1))
    }

    "stop source if connection stopped" in new SourceScope {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectComplete()
    }

    "stop source if error while reading" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()
    }

    "stop source if error while subscribing" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo

      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()

      override def position = Some(Position(0))
    }

    "stop source if error while catching up" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)

      connection expectMsg readEvents(0)
      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()
    }

    "stop source if error while live processing" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)

      connection reply Failure(failure)

      expectError(failure)
      expectNoActivity()

      override def position = Some(Position(0))
    }

    "unsubscribe when buffer is full and ignore appearing events" in new SourceScope {

      val testEvent1 = newEvent(1337)
      val testEvent2 = newEvent(1338)
      val testEvent3 = newEvent(1339)
      val testEvent4 = newEvent(1340)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1336)
      connection.expectNoMessage()
      connection reply StreamEventAppeared(testEvent1)
      connection reply StreamEventAppeared(testEvent2)
      connection reply StreamEventAppeared(testEvent3)
      connection expectMsg Unsubscribe
      connection reply StreamEventAppeared(testEvent4)
      connection reply Unsubscribed
      expectEvent(testEvent1)
      expectEvent(testEvent2)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1340)
      connection expectMsg readEvents(1339)
      connection reply readCompleted(1340, 1341, testEvent3, testEvent4)
      connection.expectNoMessage()
      expectEvent(testEvent3)
      expectEvent(testEvent4)

      override def position = Some(Position.Last)
    }

    "resubscribe from same position" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection reply subscribeCompleted(0)
      expectNoActivity()

      override def position = Some(Position(0))
    }

    "resubscribe from different position" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1)
      connection expectMsg readEvents(0)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event2)
      connection reply readCompleted(0, 3, event0, event1, event2)
      expectEvent(event1)
      expectEvent(event2)

      override def position = Some(Position(0))
    }

    "resubscribe correctly if unexpectedly unsubscribed" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection expectMsg readEvents(0)
      connection reply readCompleted(3, 3, event0, event1, event2)
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
      connection reply StreamEventAppeared(testEvent1)
      expectEvent(testEvent1)
      connection reply Unsubscribed
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1339)
      connection expectMsg readEvents(1337)
      connection reply readCompleted(1339, 1339, testEvent1, testEvent2)
      expectEvent(testEvent2)

      override def position = Some(Position.Last)
    }

    "resubscribe correctly if unexpectedly unsubscribed while catching up" in new SourceScope {

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(2)
      connection expectMsg readEvents(0)
      connection reply readCompleted(2, 2, event0, event1)
      connection expectMsg readEvents(2)

      expectEvent(event0)
      expectEvent(event1)

      connection reply Unsubscribed
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(3)
      connection expectMsg readEvents(1)
      connection reply readCompleted(4, 4, event2, event3)

      expectEvent(event2)
      expectEvent(event3)

    }

    "ignore resubscribed while catching up" in new SourceScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection expectMsg readEvents(0)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event2)
      connection reply StreamEventAppeared(event3)
      connection reply subscribeCompleted(1)
      connection reply StreamEventAppeared(event1)
      connection reply StreamEventAppeared(event2)
      connection reply StreamEventAppeared(event3)
      connection reply readCompleted(0, 3, event0, event1, event2)

      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)
    }

    "use credentials if given" in new SourceScope {
      connection expectMsg readEvents(0).withCredentials(credentials.get)
      connection reply readCompleted(0, 0)
      connection expectMsg subscribeTo.withCredentials(credentials.get)

      override def credentials = Some(UserCredentials("login", "password"))
    }

  }

  "AllStreamsSource finite" should {

    "stop immediately if last position passed" in new FiniteSubscriptionScope {
      connection.expectNoMessage(duration)
      expectComplete()

      override def position = Some(Position.Last)
    }

    "stop when no more events left" in new FiniteSubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 0)
      expectComplete()
    }

    "stop when retrieved last event" in new FiniteSubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, 2, event0, event1)
      expectEvent(event0)
      expectEvent(event1)
      connection expectMsg readEvents(2)
      connection reply readCompleted(2, 2)
      expectComplete()
    }

  }

  private trait SourceScope extends AbstractSourceScope[IndexedEvent] {

    lazy val streamId = EventStream.All
    def position: Option[Position] = None

    def createSource(): Source[IndexedEvent, NotUsed] =
      Source.fromGraph(new AllStreamsSourceStage(
        connection.ref,
        position,
        credentials,
        Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos),
        infinite
      ))

    ///

    def newEvent(x: Long) =
      IndexedEvent(mock[Event], Position.Exact(x))

    def readEvents(x: Long) =
      ReadAllEvents(Position(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readCompleted(position: Long, next: Long, events: IndexedEvent*) =
      ReadAllEventsCompleted(events.toList, Position.Exact(position), Position.Exact(next), Forward)

    def subscribeCompleted(lastCommit: Long) = SubscribeToAllCompleted(lastCommit)

  }

  private trait FiniteSubscriptionScope extends SourceScope {
    override def infinite = false
  }
}
