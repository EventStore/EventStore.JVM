package eventstore

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import akka.testkit._
import akka.actor.ActorSystem
import ReadDirection.Forward
import CatchUpSubscription._
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class StreamCatchUpSubscriptionActorSpec extends SpecificationWithJUnit with Mockito {
  "catch up subscription actor" should {

    "read events from given position" in new StreamCatchUpScope(Some(123)) {
      connection expectMsg readStreamEvents(123)
    }

    "read events from start if no position given" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
    }

    "ignore read events with event number out of interest" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! readStreamEventsSucceed(3, false, re0, re1, re2)
      expectEvent(re0)
      expectEvent(re1)
      expectEvent(re2)

      connection expectMsg readStreamEvents(3)

      actor ! readStreamEventsSucceed(5, false, re0, re1, re2, re3, re4)

      expectEvent(re3)
      expectEvent(re4)

      connection expectMsg readStreamEvents(5)

      actor ! readStreamEventsSucceed(5, false, re0, re1, re2, re3, re4)

      expectNoMsg(duration)
      connection expectMsg readStreamEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsSucceed(3, false, re0, re1, re2)
      expectEvent(re2)
      expectNoMsg(duration)

      connection expectMsg readStreamEvents(3)
    }

    "read events until none left and subscribe to new ones" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(2, false, re1)

      expectEvent(re1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsSucceed(2, true)

      connection.expectMsg(subscribeTo)
    }

    "subscribe to new events if nothing to read" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)
      connection.expectMsg(subscribeTo)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events as soon as stop received" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "ignore read events after stop received" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      val position = 1
      actor ! readStreamEventsSucceed(2, false, re1)

      expectNoActivity
    }

    "catch events that appear in between reading and subscribing" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsSucceed(2, false, re0, re1)

      expectEvent(re0)
      expectEvent(re1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! subscribeToStreamCompleted(4)

      connection expectMsg readStreamEvents(2)

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(3, false, re1, re2)
      expectEvent(re2)

      connection expectMsg readStreamEvents(3)

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(6, false, re3, re4, re5)

      expectEvent(re3)
      expectEvent(re4)
      expectMsg(LiveProcessingStarted)
      expectEvent(re5)
      expectEvent(re6)

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)

      expectNoActivity
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      connection.expectMsg(subscribeTo)
      actor ! Stop

      expectNoActivity

      actor ! subscribeToStreamCompleted(1)

      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "not unsubscribe if subscription failed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      connection.expectMsg(subscribeTo)
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)

      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoActivity
    }

    "not unsubscribe if subscription failed if stop received " in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      actor ! Stop
      connection.expectMsg(subscribeTo)

      expectNoActivity

      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)
      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoActivity
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsSucceed(2, false, re0, re1)

      expectEvent(re0)
      expectEvent(re1)

      connection expectMsg readStreamEvents(2)

      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! subscribeToStreamCompleted(5)

      connection expectMsg readStreamEvents(2)

      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)

      actor ! Stop

      expectNoMsg(duration)
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "continue with subscription if no events appear in between reading and subscribing" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)

      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "forward events while subscribed" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(re1)
      expectEvent(re1)

      expectNoMsg(duration)

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      expectEvent(re2)
      expectEvent(re3)
    }

    "ignore wrong events while subscribed" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      actor ! subscribeToStreamCompleted(2)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(re0)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re2)
      expectEvent(re2)
      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re1)
      actor ! StreamEventAppeared(re3)
      expectEvent(re3)
      actor ! StreamEventAppeared(re5)
      expectEvent(re5)
      actor ! StreamEventAppeared(re4)
      expectNoMsg(duration)
    }

    "stop subscription when stop received" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      val position = 1
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      actor ! subscribeToStreamCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! StreamEventAppeared(re2)
      expectEvent(re2)

      actor ! Stop
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoActivity
    }

    "handle properly linked events resolveLinkTos = true" in todo
  }

  abstract class StreamCatchUpScope(eventNumber: Option[Int] = None)
    extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val duration = FiniteDuration(1, SECONDS)
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()
    val streamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)
    val actor = TestActorRef(new StreamCatchUpSubscriptionActor(
      connection.ref,
      testActor,
      streamId,
      eventNumber.map(EventNumber.apply),
      resolveLinkTos,
      readBatchSize))

    val re0 = resolvedEvent(0)
    val re1 = resolvedEvent(1)
    val re2 = resolvedEvent(2)
    val re3 = resolvedEvent(3)
    val re4 = resolvedEvent(4)
    val re5 = resolvedEvent(5)
    val re6 = resolvedEvent(6)

    def resolvedEvent(number: Int) = {
      val eventRecord = EventRecord(streamId, EventNumber(number), mock[Event])
      ResolvedEvent(eventRecord, None, Position(number))
    }

    def readStreamEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def readStreamEventsSucceed(next: Int, endOfStream: Boolean, events: ResolvedEvent*) =
      ReadStreamEventsSucceed(
        resolvedIndexedEvents = events.map(x => ResolvedIndexedEvent(x.eventRecord, x.link)),
        nextEventNumber = EventNumber(next),
        lastEventNumber = mock[EventNumber.Exact],
        endOfStream = endOfStream,
        lastCommitPosition = next /*TODO*/ ,
        direction = Forward)


    def expectNoActivity {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    def expectEvent(x: ResolvedEvent) {
      expectMsg(ResolvedIndexedEvent(x.eventRecord, x.link))
    }

    def subscribeToStreamCompleted(x: Int) = SubscribeToStreamCompleted(x, Some(EventNumber(x)))

    implicit class RichTestKit(testKit: TestKitBase) {
      def expectEvent(x: ResolvedEvent) {
        testKit.expectMsg(ResolvedIndexedEvent(x.eventRecord, x.link))
      }
    }
  }
}
