package eventstore

import ReadDirection.Forward
import akka.actor.Status.Failure
import akka.testkit.TestProbe

class StreamCatchUpSubscriptionActorSpec extends AbstractCatchUpSubscriptionActorSpec {
  "catch up subscription actor" should {

    "read events from given position" in new StreamCatchUpScope(Some(123)) {
      connection expectMsg readStreamEvents(123)
    }

    "read events from start if no position given" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
    }

    "ignore read events with event number out of interest" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! readStreamEventsCompleted(3, false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readStreamEvents(3)

      actor ! readStreamEventsCompleted(5, false, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readStreamEvents(5)

      actor ! readStreamEventsCompleted(5, false, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readStreamEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsCompleted(3, false, event0, event1, event2)
      expectEvent(event2)
      expectNoMsg(duration)

      connection expectMsg readStreamEvents(3)
    }

    "read events until none left and subscribe to new ones" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(2, false, event1)

      expectEvent(event1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsCompleted(2, true)

      connection expectMsg subscribeTo
    }

    "subscribe to new events if nothing to read" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)

      expectMsg(Cs.LiveProcessingStarted)
    }

    "stop reading events if actor stopped" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "catch events that appear in between reading and subscribing" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsCompleted(2, true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(4)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readStreamEventsCompleted(3, false, event1, event2)
      expectEvent(event2)

      connection expectMsg readStreamEvents(3)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readStreamEventsCompleted(6, false, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectMsg(Cs.LiveProcessingStarted)
      expectEvent(event5)
      expectEvent(event6)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)

      expectNoActivity
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)
      connection expectMsg subscribeTo
      actor.stop()
      expectActorTerminated()
    }

    "not unsubscribe if subscription failed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)

      connection expectMsg subscribeTo
      actor ! Failure(EventStoreException(EventStoreError.AccessDenied))
      expectActorTerminated()
    }

    "not unsubscribe if subscription failed if stop received " in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)
      connection expectMsg subscribeTo
      expectNoActivity
      actor ! Failure(EventStoreException(EventStoreError.AccessDenied))
      expectActorTerminated()
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readStreamEvents(2)

      actor ! readStreamEventsCompleted(2, true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(5)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)

      actor.stop()
      connection.expectMsg(UnsubscribeFromStream)
      expectActorTerminated()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      expectMsg(Cs.LiveProcessingStarted)

      expectNoActivity
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)

      actor ! readStreamEventsCompleted(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      expectMsg(Cs.LiveProcessingStarted)

      expectNoActivity
    }

    "forward events while subscribed" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      expectMsg(Cs.LiveProcessingStarted)

      actor ! streamEventAppeared(event1)
      expectEvent(event1)

      expectNoMsg(duration)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(2)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsCompleted(position, true)

      expectMsg(Cs.LiveProcessingStarted)

      actor ! streamEventAppeared(event0)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event2)
      expectEvent(event2)
      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event3)
      expectEvent(event3)
      actor ! streamEventAppeared(event5)
      expectEvent(event5)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)
    }

    "stop subscription when actor stopped and subscribed" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsCompleted(1, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(1)
      expectMsg(Cs.LiveProcessingStarted)

      actor ! streamEventAppeared(event2)
      expectEvent(event2)

      actor.stop()
      connection.expectMsg(UnsubscribeFromStream)
      expectActorTerminated()
    }

    "stop actor if read error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsFailed()
      expectActorTerminated()
    }

    "stop actor if subscription error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)

      connection expectMsg subscribeTo
      actor ! Failure(EventStoreException(EventStoreError.AccessDenied))

      expectActorTerminated()
    }

    "stop actor if catchup read error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsCompleted(0, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsFailed(EventStoreError.StreamNotFound)

      connection expectMsg UnsubscribeFromStream

      expectActorTerminated()
    }

    "stop actor if connection stopped" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      system stop connection.ref
      expectActorTerminated()
    }

    "stop actor if client stopped" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      expectActorTerminated(probe)
    }

    "not stop subscription if actor stopped and not yet subscribed" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor.stop()
      expectActorTerminated()
    }
  }

  abstract class StreamCatchUpScope(eventNumber: Option[Int] = None) extends AbstractScope {
    lazy val streamId = EventStream(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    def props = StreamCatchUpSubscriptionActor.props(
      connection = connection.ref,
      client = testActor,
      streamId = streamId,
      fromNumberExclusive = eventNumber.map(EventNumber.apply),
      resolveLinkTos = resolveLinkTos,
      readBatchSize = readBatchSize)

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)

    def expectEvent(x: Event) = expectMsg(Cs.StreamEvent(x))

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber(number), mock[EventData])

    def readStreamEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readStreamEventsCompleted(next: Int, endOfStream: Boolean, events: Event*) = ReadStreamEventsCompleted(
      events = events.toList,
      nextEventNumber = EventNumber(next),
      lastEventNumber = mock[EventNumber.Exact],
      endOfStream = endOfStream,
      lastCommitPosition = next /*TODO*/ ,
      direction = Forward)

    def readStreamEventsFailed(reason: EventStoreError.Value = EventStoreError.StreamDeleted) =
      Failure(EventStoreException(reason, None))

    def subscribeToStreamCompleted(x: Int) = SubscribeToStreamCompleted(x, Some(EventNumber(x)))
  }
}
