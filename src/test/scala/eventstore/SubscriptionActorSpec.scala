package eventstore

import ReadDirection.Forward
import akka.testkit.TestProbe

class SubscriptionActorSpec extends AbstractSubscriptionActorSpec {
  "catch up subscription actor" should {

    "read events from given position" in new SubscriptionScope {
      connection expectMsg readEvents(123)

      override def position = Some(Position(123))
    }

    "read events from start if no position given" in new SubscriptionScope {
      connection expectMsg readEvents(0)
    }

    "subscribe if last position given" in new SubscriptionScope {
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)
      connection.expectNoMsg()
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event0)
      actor ! StreamEventAppeared(event2)
      expectMsg(LiveProcessingStarted)
      expectEvent(event1)
      expectEvent(event2)

      override def position = Some(Position.Last)
    }

    "ignore read events with position out of interest" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      actor ! readCompleted(0, 3, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      actor ! readCompleted(3, 5, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readEvents(5)

      actor ! readCompleted(3, 5, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readEvents(5)
    }

    "ignore read events with position out of interest when start position is given" in new SubscriptionScope {
      connection expectMsg readEvents(1)

      actor ! readCompleted(0, 3, event0, event1, event2)
      expectEvent(event2)
      expectNoMsg(duration)

      connection expectMsg readEvents(3)

      override def position = Some(Position(1))
    }

    "read events until none left and subscribe to new ones" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      val nextPosition = 2
      actor ! readCompleted(1, nextPosition, event1)

      expectEvent(event1)

      connection expectMsg readEvents(nextPosition)
      actor ! readCompleted(nextPosition, nextPosition)

      connection.expectMsg(subscribeTo)
    }

    "subscribe to new events if nothing to read" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events as soon as stop received" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor.stop()
      expectTerminated(actor)
    }

    "catch events that appear in between reading and subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      actor ! readCompleted(0, 2, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)
      actor ! readCompleted(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(4)

      connection expectMsg readEvents(2)

      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event3)
      actor ! StreamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readCompleted(2, 3, event1, event2)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      actor ! StreamEventAppeared(event5)
      actor ! StreamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readCompleted(3, 6, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectMsg(LiveProcessingStarted)
      expectEvent(event5)
      expectEvent(event6)

      actor ! StreamEventAppeared(event5)
      actor ! StreamEventAppeared(event6)

      expectNoActivity()
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      connection.expectMsg(subscribeTo)
      actor.stop()
      expectTerminated(actor)
    }

    "not unsubscribe if subscription failed if stop received " in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection.expectMsg(subscribeTo)
      actor.stop()
      expectTerminated(actor)
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      actor ! readCompleted(0, 2, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)

      actor ! readCompleted(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(5)

      connection expectMsg readEvents(2)

      actor ! StreamEventAppeared(event3)
      actor ! StreamEventAppeared(event4)

      actor.stop()
      connection expectMsg Unsubscribe
      expectTerminated(actor)
    }

    "continue with subscription if no events appear in between reading and subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      expectMsg(LiveProcessingStarted)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in
      new SubscriptionScope {
        connection expectMsg readEvents(1)

        actor ! readCompleted(1, 1)

        connection.expectMsg(subscribeTo)
        expectNoMsg(duration)

        actor ! SubscribeToAllCompleted(1)

        expectMsg(LiveProcessingStarted)

        expectNoActivity()

        override def position = Some(Position(1))
      }

    "forward events while subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event1)
      expectEvent(event1)

      expectNoMsg(duration)

      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(1)
      actor ! readCompleted(1, 1)

      connection.expectMsg(subscribeTo)
      actor ! SubscribeToAllCompleted(2)

      connection expectMsg readEvents(1)
      actor ! readCompleted(1, 1)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event0)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event2)
      expectEvent(event2)
      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event3)
      expectEvent(event3)
      actor ! StreamEventAppeared(event5)
      expectEvent(event5)
      actor ! StreamEventAppeared(event4)
      expectNoMsg(duration)

      override def position = Some(Position(1))
    }

    "stop subscription when stop received" in new SubscriptionScope {
      connection expectMsg readEvents(1)

      actor ! readCompleted(1, 1)

      connection.expectMsg(subscribeTo)
      actor ! SubscribeToAllCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event2)
      expectEvent(event2)

      actor.stop()
      connection expectMsg Unsubscribe
      expectTerminated(actor)

      override def position = Some(Position(1))
    }

    "stop actor if connection stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectTerminated(actor)
    }

    "not stop subscription if actor stopped and not yet subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor.stop()
      expectTerminated(actor)
    }

    "stop actor if client stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      probe.expectTerminated(actor)
    }

    "stop actor if error while reading" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      expectTerminatedOnFailure()
    }

    "stop actor if error while subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo

      expectTerminatedOnFailure()

      override def position = Some(Position(0))
    }

    "stop actor if error while catching up" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)

      connection expectMsg readEvents(0)
      expectTerminatedOnFailure(expectUnsubscribe = true)
    }

    "stop actor if error while live processing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)

      expectMsg(LiveProcessingStarted)
      expectTerminatedOnFailure(expectUnsubscribe = true)

      override def position = Some(Position(0))
    }

    "re-read if reconnected while reading" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      reconnect()
      connection expectMsg readEvents(0)
      expectNoActivity()
    }

    "re-subscribe reconnected while subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo
      reconnect()
      connection expectMsg subscribeTo
      expectNoActivity()

      override def position = Some(Position(0))
    }

    "re-subscribe reconnected while subscribing from last" in new SubscriptionScope {
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)
      reconnect()
      connection expectMsg subscribeTo

      override def position = Some(Position.Last)
    }

    "re-subscribe if reconnected while catching up" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)

      connection expectMsg readEvents(0)
      reconnect()

      connection expectMsg subscribeTo
    }

    "re-subscribe if reconnected while live processing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo
      actor ! SubscribeToAllCompleted(0)

      expectMsg(LiveProcessingStarted)

      reconnect()
      connection expectMsg subscribeTo

      override def position = Some(Position(0))
    }

    "use credentials if given" in new SubscriptionScope {
      connection expectMsg readEvents(0).withCredentials(credentials.get)
      actor ! readCompleted(0, 0)
      connection expectMsg subscribeTo.withCredentials(credentials.get)

      override def credentials = Some(UserCredentials("login", "password"))
    }
  }

  trait SubscriptionScope extends AbstractScope {
    def props = SubscriptionActor.props(
      connection = connection.ref,
      client = testActor,
      fromPositionExclusive = position,
      resolveLinkTos = resolveLinkTos,
      credentials = credentials,
      readBatchSize = readBatchSize)

    lazy val streamId = EventStream.All

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)

    def expectEvent(x: IndexedEvent) = expectMsg(x)

    def newEvent(x: Long) = IndexedEvent(mock[Event], Position(x))

    def readEvents(x: Long) = ReadAllEvents(Position(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readCompleted(position: Long, next: Long, events: IndexedEvent*) =
      ReadAllEventsCompleted(events.toList, Position(position), Position(next), Forward)

    def position: Option[Position] = None
  }
}