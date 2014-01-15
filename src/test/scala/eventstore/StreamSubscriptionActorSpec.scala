package eventstore

import ReadDirection.Forward
import Subscription.LiveProcessingStarted
import akka.actor.Status.Failure
import akka.testkit.TestProbe

class StreamSubscriptionActorSpec extends AbstractSubscriptionActorSpec {
  "catch up subscription actor" should {

    "read events from given position" in new SubscriptionScope(Some(EventNumber(123))) {
      connection expectMsg readEvents(123)
    }

    "read events from start if no position given" in new SubscriptionScope {
      connection expectMsg readEvents(0)
    }

    "subscribe if last position given" in new SubscriptionScope(Some(EventNumber.Last)) {
      connection expectMsg subscribeTo
      actor ! SubscribeToStreamCompleted(0)
      connection.expectNoMsg()
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event0)
      actor ! streamEventAppeared(event2)
      expectMsg(LiveProcessingStarted)
      expectEvent(event1)
      expectEvent(event2)
    }

    "ignore read events with event number out of interest" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      actor ! readCompleted(3, false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      actor ! readCompleted(5, false, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readEvents(5)

      actor ! readCompleted(5, false, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in
      new SubscriptionScope(Some(EventNumber(1))) {
        connection expectMsg readEvents(1)

        actor ! readCompleted(3, false, event0, event1, event2)
        expectEvent(event2)
        expectNoMsg(duration)

        connection expectMsg readEvents(3)
      }

    "read events until none left and subscribe to new ones" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(2, false, event1)

      expectEvent(event1)

      connection expectMsg readEvents(2)
      actor ! readCompleted(2, endOfStream = true)

      connection expectMsg subscribeTo
    }

    "subscribe to new events if nothing to read" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events if actor stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "catch events that appear in between reading and subscribing" in new SubscriptionScope() {
      connection expectMsg readEvents(0)

      val position = 1
      actor ! readCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)
      actor ! readCompleted(2, endOfStream = true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(4)

      connection expectMsg readEvents(2)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readCompleted(3, false, event1, event2)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readCompleted(6, false, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectMsg(LiveProcessingStarted)
      expectEvent(event5)
      expectEvent(event6)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)

      expectNoActivity()
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      actor.stop()
      expectActorTerminated()
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new SubscriptionScope() {
      connection expectMsg readEvents(0)

      val position = 1
      actor ! readCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)

      actor ! readCompleted(2, endOfStream = true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(5)

      connection expectMsg readEvents(2)

      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)

      actor.stop()
      connection.expectMsg(Unsubscribe)
      expectActorTerminated()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new SubscriptionScope() {
      val position = 0
      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      expectMsg(LiveProcessingStarted)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in
      new SubscriptionScope(Some(EventNumber(1))) {
        val position = 1
        connection expectMsg readEvents(position)

        actor ! readCompleted(position, endOfStream = true)

        connection expectMsg subscribeTo
        expectNoMsg(duration)

        actor ! subscribeToStreamCompleted(1)

        expectMsg(LiveProcessingStarted)

        expectNoActivity()
      }

    "forward events while subscribed" in new SubscriptionScope() {
      val position = 0
      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event1)
      expectEvent(event1)

      expectNoMsg(duration)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new SubscriptionScope(Some(EventNumber(1))) {
      val position = 1
      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(2)

      connection expectMsg readEvents(position)
      actor ! readCompleted(position, endOfStream = true)

      expectMsg(LiveProcessingStarted)

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

    "stop subscription when actor stopped and subscribed" in new SubscriptionScope(Some(EventNumber(1))) {
      connection expectMsg readEvents(1)

      actor ! readCompleted(1, endOfStream = true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event2)
      expectEvent(event2)

      actor.stop()
      connection.expectMsg(Unsubscribe)
      expectActorTerminated()
    }

    "stop actor if connection stopped" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectTerminated(actor)
    }

    "stop actor if connection stopped" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectActorTerminated()
    }

    "stop actor if client stopped" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      expectActorTerminated(probe)
    }

    "not stop subscription if actor stopped and not yet subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "stop actor if error while reading" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      expectTerminatedOnFailure()
    }

    "stop actor if error while subscribing" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo

      expectTerminatedOnFailure()
    }

    "stop actor if error while catching up" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      actor ! SubscribeToStreamCompleted(0)

      expectMsg(LiveProcessingStarted)
      expectTerminatedOnFailure(expectUnsubscribe = true)
    }

    "stop actor if error while live processing" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      actor ! SubscribeToStreamCompleted(0)

      expectMsg(LiveProcessingStarted)
      expectTerminatedOnFailure(expectUnsubscribe = true)
    }

    "re-read reconnected while reading" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      reconnect()
      connection expectMsg readEvents(0)
      expectNoActivity()
    }

    "re-subscribe reconnected while subscribing" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      reconnect()
      connection expectMsg subscribeTo
      expectNoActivity()
    }

    "re-subscribe reconnected while subscribing from last" in new SubscriptionScope(Some(EventNumber.Last)) {
      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(0)
      reconnect()
      connection expectMsg subscribeTo
    }

    "re-subscribe if reconnected while catching up" in new SubscriptionScope() {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      actor ! SubscribeToStreamCompleted(0)
      reconnect()
      connection expectMsg subscribeTo
    }

    "re-subscribe if reconnected while live processing" in new SubscriptionScope(Some(EventNumber(0))) {
      connection expectMsg readEvents(0)
      actor ! readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      actor ! SubscribeToStreamCompleted(0)
      expectMsg(LiveProcessingStarted)
      reconnect()
      connection expectMsg subscribeTo
    }
  }

  abstract class SubscriptionScope(eventNumber: Option[EventNumber] = None) extends AbstractScope {
    lazy val streamId = EventStream(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    def props = StreamSubscriptionActor.props(
      connection = connection.ref,
      client = testActor,
      streamId = streamId,
      fromNumberExclusive = eventNumber,
      resolveLinkTos = resolveLinkTos,
      readBatchSize = readBatchSize)

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)

    def expectEvent(x: Event) = expectMsg(x)

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber(number), mock[EventData])

    def readEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readCompleted(next: Int, endOfStream: Boolean, events: Event*) = ReadStreamEventsCompleted(
      events = events.toList,
      nextEventNumber = EventNumber(next),
      lastEventNumber = mock[EventNumber.Exact],
      endOfStream = endOfStream,
      lastCommitPosition = next /*TODO*/ ,
      direction = Forward)

    def subscribeToStreamCompleted(x: Int) = SubscribeToStreamCompleted(x, Some(EventNumber(x)))
  }
}
