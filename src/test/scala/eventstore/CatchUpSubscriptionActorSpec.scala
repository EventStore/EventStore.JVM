package eventstore

import org.specs2.mutable.SpecificationWithJUnit
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
class CatchUpSubscriptionActorSpec extends SpecificationWithJUnit with Mockito {
  "catch up subscription actor" should {

    "read events from given position" in new CatchUpScope(Some(123)) {
      connection expectMsg readAllEvents(123)
    }

    "read events from start if no position given" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
    }

    "ignore read events with position out of interest" in new CatchUpScope {
      connection expectMsg readAllEvents(0)

      actor ! readAllEventsSucceed(0, 3, re0, re1, re2)
      expectMsg(re0)
      expectMsg(re1)
      expectMsg(re2)

      connection expectMsg readAllEvents(3)

      actor ! readAllEventsSucceed(3, 5, re0, re1, re2, re3, re4)

      expectMsg(re3)
      expectMsg(re4)

      connection expectMsg readAllEvents(5)

      actor ! readAllEventsSucceed(3, 5, re0, re1, re2, re3, re4)

      expectNoMsg(duration)
      connection expectMsg readAllEvents(5)
    }

    "ignore read events with position out of interest when start position is given" in new CatchUpScope(Some(1)) {
      connection expectMsg readAllEvents(1)

      actor ! readAllEventsSucceed(0, 3, re0, re1, re2)
      expectMsg(re2)
      expectNoMsg(duration)

      connection expectMsg readAllEvents(3)
    }

    "read events until none left and subscribe to new ones" in new CatchUpScope {
      connection expectMsg readAllEvents(0)
      val nextPosition = 2
      actor ! readAllEventsSucceed(1, nextPosition, re1)

      expectMsg(re1)

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

      actor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "ignore read events after stop received" in new CatchUpScope {
      connection expectMsg readAllEvents(0)

      actor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      actor ! readAllEventsSucceed(1, 2, re1)

      expectNoActivity
    }

    "catch events that appear in between reading and subscribing" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)

      val position = 1
      actor ! readAllEventsSucceed(0, 2, re0, re1)

      expectMsg(re0)
      expectMsg(re1)

      connection expectMsg readAllEvents(2)
      actor ! readAllEventsSucceed(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(4)

      connection expectMsg readAllEvents(2)

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)
      expectNoMsg(duration)

      actor ! readAllEventsSucceed(2, 3, re1, re2)
      expectMsg(re2)

      connection expectMsg readAllEvents(3)

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)
      expectNoMsg(duration)

      actor ! readAllEventsSucceed(3, 6, re3, re4, re5)

      expectMsg(re3)
      expectMsg(re4)
      expectMsg(LiveProcessingStarted)
      expectMsg(re5)
      expectMsg(re6)

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)

      expectNoActivity
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection.expectMsg(subscribeTo)
      actor ! Stop

      expectNoActivity

      actor ! SubscribeToAllCompleted(1)

      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "not unsubscribe if subscription failed" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      connection.expectMsg(subscribeTo)
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)

      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoActivity
    }

    "not unsubscribe if subscription failed if stop received " in new CatchUpScope() {
      connection expectMsg readAllEvents(0)
      actor ! readAllEventsSucceed(0, 0)

      actor ! Stop
      connection.expectMsg(subscribeTo)

      expectNoActivity

      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)
      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoActivity
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new CatchUpScope() {
      connection expectMsg readAllEvents(0)

      val position = 1
      actor ! readAllEventsSucceed(0, 2, re0, re1)

      expectMsg(re0)
      expectMsg(re1)

      connection expectMsg readAllEvents(2)

      actor ! readAllEventsSucceed(2, 2)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! SubscribeToAllCompleted(5)

      connection expectMsg readAllEvents(2)

      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)

      actor ! Stop

      expectNoMsg(duration)
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

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

      actor ! StreamEventAppeared(re1)
      expectMsg(re1)

      expectNoMsg(duration)

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      expectMsg(re2)
      expectMsg(re3)
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

      actor ! StreamEventAppeared(re0)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re2)
      expectMsg(re2)
      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re3)
      expectMsg(re3)
      actor ! StreamEventAppeared(re5)
      expectMsg(re5)
      actor ! StreamEventAppeared(re4)
      expectNoMsg(duration)
    }

    "stop subscription when stop received" in new CatchUpScope(Some(1)) {
      connection expectMsg readAllEvents(1)

      val position = 1
      actor ! readAllEventsSucceed(position, position)

      connection.expectMsg(subscribeTo)
      actor ! SubscribeToAllCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(re2)
      expectMsg(re2)

      actor ! Stop
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "handle properly linked events resolveLinkTos = true" in todo
  }

  abstract class CatchUpScope(position: Option[Long] = None)
    extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val duration = FiniteDuration(1, SECONDS)
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()
    val actor = TestActorRef(new CatchUpSubscriptionActor(connection.ref, testActor, position.map(Position.apply), resolveLinkTos, readBatchSize))

    val re0 = resolvedEvent(0)
    val re1 = resolvedEvent(1)
    val re2 = resolvedEvent(2)
    val re3 = resolvedEvent(3)
    val re4 = resolvedEvent(4)
    val re5 = resolvedEvent(5)
    val re6 = resolvedEvent(6)

    def resolvedEvent(x: Long) = ResolvedEvent(mock[EventRecord], None, Position(x))
    def readAllEvents(x: Long) = ReadAllEvents(Position(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
    def subscribeTo = SubscribeTo(EventStream.All, resolveLinkTos = resolveLinkTos)

    def readAllEventsSucceed(position: Long, next: Long, events: ResolvedEvent*) =
      ReadAllEventsSucceed(Position(position), events, Position(next), Forward)

    def expectNoActivity {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }
  }
}
