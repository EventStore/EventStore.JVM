package eventstore

import akka.testkit.TestProbe
import ReadDirection.Forward
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class CatchUpSubscriptionActorSpec extends AbstractCatchUpSubscriptionActorSpec {
  "catch up subscription actor" should {

    "read events from given position" in new CatchUpScope(Some(123)) {
      connection expectMsg readAllEvents(123)
    }

    "read events from start if no position given" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
    }

    "ignore read events with position out of interest" in new CatchUpScope {
      connection expectMsg readAllEvents(0)

      actor ! readAllEventsSucceed(0, 3, event0, event1, event2)
      expectMsg(event0)
      expectMsg(event1)
      expectMsg(event2)

      connection expectMsg readAllEvents(3)

      actor ! readAllEventsSucceed(3, 5, event0, event1, event2, event3, event4)

      expectMsg(event3)
      expectMsg(event4)

      connection expectMsg readAllEvents(5)

      actor ! readAllEventsSucceed(3, 5, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readAllEvents(5)
    }

    "ignore read events with position out of interest when start position is given" in new CatchUpScope(Some(1)) {
      connection expectMsg readAllEvents(1)

      actor ! readAllEventsSucceed(0, 3, event0, event1, event2)
      expectMsg(event2)
      expectNoMsg(duration)

      connection expectMsg readAllEvents(3)
    }

    "read events until none left and subscribe to new ones" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      val nextPosition = 2
      actor ! readAllEventsSucceed(1, nextPosition, event1)

      expectMsg(event1)

      connection expectMsg readAllEvents(nextPosition)
      actor ! readAllEventsSucceed(nextPosition, nextPosition)

      connection.expectMsg(subscribeTo)
    }

    "subscribe to new events if nothing to read" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      val position = 0
      actor ! readAllEventsSucceed(position, position)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(position, position)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events as soon as stop received" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "catch events that appear in between reading and subscribing" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)

      val position = 1
      actor ! readAllEventsSucceed(0, 2, event0, event1)

      expectMsg(event0)
      expectMsg(event1)

      connection expectMsg readAllEvents(2)
      actor ! readAllEventsSucceed(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(4)

      connection expectMsg readAllEvents(2)

      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event3)
      actor ! StreamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readAllEventsSucceed(2, 3, event1, event2)
      expectMsg(event2)

      connection expectMsg readAllEvents(3)

      actor ! StreamEventAppeared(event5)
      actor ! StreamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readAllEventsSucceed(3, 6, event3, event4, event5)

      expectMsg(event3)
      expectMsg(event4)
      expectMsg(LiveProcessingStarted)
      expectMsg(event5)
      expectMsg(event6)

      actor ! StreamEventAppeared(event5)
      actor ! StreamEventAppeared(event6)

      expectNoActivity
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection.expectMsg(subscribeTo)
      actor.stop()
      expectActorTerminated()
    }

    "not unsubscribe if subscription failed" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection.expectMsg(subscribeTo)
      actor ! SubscriptionDropped(SubscriptionDropped.Reason.AccessDenied)

      expectActorTerminated()
    }

    "not unsubscribe if subscription failed if stop received " in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)
      connection.expectMsg(subscribeTo)
      actor.stop()
      expectActorTerminated()
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)

      val position = 1
      actor ! readAllEventsSucceed(0, 2, event0, event1)

      expectMsg(event0)
      expectMsg(event1)

      connection expectMsg readAllEvents(2)

      actor ! readAllEventsSucceed(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(5)

      connection expectMsg readAllEvents(2)

      actor ! StreamEventAppeared(event3)
      actor ! StreamEventAppeared(event4)

      actor.stop()
      connection expectMsg UnsubscribeFromStream
      expectActorTerminated()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new CatchUpScope() {
      val position = 0
      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in new CatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readAllEvents(position)

      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! SubscribeToAllCompleted(1)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "forward events while subscribed" in new CatchUpScope() {
      val position = 0
      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! SubscribeToAllCompleted(1)

      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event1)
      expectMsg(event1)

      expectNoMsg(duration)

      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event3)
      expectMsg(event2)
      expectMsg(event3)
    }

    "ignore wrong events while subscribed" in new CatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      actor ! SubscribeToAllCompleted(2)

      connection expectMsg readAllEvents(position)
      actor ! readAllEventsSucceed(position, position)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event0)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event2)
      expectMsg(event2)
      actor ! StreamEventAppeared(event2)
      actor ! StreamEventAppeared(event1)
      actor ! StreamEventAppeared(event3)
      expectMsg(event3)
      actor ! StreamEventAppeared(event5)
      expectMsg(event5)
      actor ! StreamEventAppeared(event4)
      expectNoMsg(duration)
    }

    "stop subscription when stop received" in new CatchUpScope(Some(1)) {
      connection expectMsg readAllEvents(1)

      val position = 1
      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      actor ! SubscribeToAllCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(event2)
      expectMsg(event2)

      actor.stop()
      connection expectMsg UnsubscribeFromStream
      expectActorTerminated()
    }

    "stop actor if read error" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsFailed
      expectActorTerminated()
    }

    "stop actor if subscription error" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection expectMsg subscribeTo
      actor ! SubscriptionDropped(SubscriptionDropped.Reason.AccessDenied)

      expectActorTerminated()
    }

    "stop actor if catchup read error" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)

      actor ! readAllEventsSucceed(0, 0)
      connection expectMsg subscribeTo

      actor ! SubscribeToAllCompleted(1)
      connection expectMsg readAllEvents(0)

      actor ! readAllEventsFailed
      connection expectMsg UnsubscribeFromStream
      expectActorTerminated()
    }

    "stop actor if connection stopped" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      system stop connection.ref
      expectActorTerminated()
    }

    "not stop subscription if actor stopped and not yet subscribed" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "stop actor if client stopped" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      expectActorTerminated(probe)
    }
  }

  abstract class CatchUpScope(position: Option[Long] = None) extends AbstractScope {
    def newActor = new CatchUpSubscriptionActor(
      connection = connection.ref,
      client = testActor,
      fromPositionExclusive = position.map(Position.apply),
      resolveLinkTos = resolveLinkTos,
      readBatchSize = readBatchSize)

    lazy val streamId = EventStream.All

    val event0 = indexedEvent(0)
    val event1 = indexedEvent(1)
    val event2 = indexedEvent(2)
    val event3 = indexedEvent(3)
    val event4 = indexedEvent(4)
    val event5 = indexedEvent(5)
    val event6 = indexedEvent(6)

    def indexedEvent(x: Long) = IndexedEvent(mock[Event], Position(x))

    def readAllEvents(x: Long) = ReadAllEvents(Position(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readAllEventsSucceed(position: Long, next: Long, events: IndexedEvent*) =
      ReadAllEventsSucceed(Position(position), events, Position(next), Forward)

    def readAllEventsFailed = ReadAllEventsFailed(ReadAllEventsFailed.Reason.Error, None, Position(0), Forward)
  }
}
