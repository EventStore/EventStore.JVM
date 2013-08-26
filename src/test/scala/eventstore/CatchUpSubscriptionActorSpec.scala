package eventstore

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.actor.ActorSystem
import ReadDirection.Forward
import CatchUpSubscription._
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class CatchUpSubscriptionActorSpec extends Specification with Mockito {
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
      expectNoActivity
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
      expectNoActivity
    }

    "not unsubscribe if subscription failed" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection.expectMsg(subscribeTo)
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)

      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))
      expectNoActivity
      actor.underlying.isTerminated must beTrue
    }

    "not unsubscribe if subscription failed if stop received " in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      actor.stop()
      connection.expectMsg(subscribeTo)

      expectNoActivity
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

      expectNoMsg(duration)
      connection.expectMsg(UnsubscribeFromStream)
      expectNoActivity
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
      connection.expectMsg(UnsubscribeFromStream)
      expectNoActivity
    }

    "not stop subscription if actor stopped and not yet subscribed" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      actor.stop()
      expectNoActivity
    }
  }

  abstract class CatchUpScope(position: Option[Long] = None)
    extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val duration = FiniteDuration(1, SECONDS)
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()
    val actor = TestActorRef(new CatchUpSubscriptionActor(connection.ref, testActor, position.map(Position.apply), resolveLinkTos, readBatchSize))

    val event0 = indexedEvent(0)
    val event1 = indexedEvent(1)
    val event2 = indexedEvent(2)
    val event3 = indexedEvent(3)
    val event4 = indexedEvent(4)
    val event5 = indexedEvent(5)
    val event6 = indexedEvent(6)

    def indexedEvent(x: Long) = IndexedEvent(mock[Event], Position(x))
    def readAllEvents(x: Long) = ReadAllEvents(Position(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
    def subscribeTo = SubscribeTo(EventStream.All, resolveLinkTos = resolveLinkTos)

    def readAllEventsSucceed(position: Long, next: Long, events: IndexedEvent*) =
      ReadAllEventsSucceed(Position(position), events, Position(next), Forward)

    def expectNoActivity {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }
  }
}
