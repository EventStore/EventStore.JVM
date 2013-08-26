package eventstore

import org.specs2.mutable.Specification
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
class StreamCatchUpSubscriptionActorSpec extends Specification with Mockito {
  "catch up subscription actor" should {

    "read events from given position" in new StreamCatchUpScope(Some(123)) {
      connection expectMsg readStreamEvents(123)
    }

    "read events from start if no position given" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
    }

    "ignore read events with event number out of interest" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! readStreamEventsSucceed(3, false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readStreamEvents(3)

      actor ! readStreamEventsSucceed(5, false, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readStreamEvents(5)

      actor ! readStreamEventsSucceed(5, false, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readStreamEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsSucceed(3, false, event0, event1, event2)
      expectEvent(event2)
      expectNoMsg(duration)

      connection expectMsg readStreamEvents(3)
    }

    "read events until none left and subscribe to new ones" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(2, false, event1)

      expectEvent(event1)

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
      actor ! readStreamEventsSucceed(2, false, event1)

      expectNoActivity
    }

    "catch events that appear in between reading and subscribing" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsSucceed(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! subscribeToStreamCompleted(4)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(3, false, event1, event2)
      expectEvent(event2)

      connection expectMsg readStreamEvents(3)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(6, false, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectMsg(LiveProcessingStarted)
      expectEvent(event5)
      expectEvent(event6)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)

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
      actor ! readStreamEventsSucceed(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readStreamEvents(2)

      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection.expectMsg(subscribeTo)

      actor ! subscribeToStreamCompleted(5)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)

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
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      actor ! subscribeToStreamCompleted(2)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

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

    "stop subscription when stop received" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      val position = 1
      actor ! readStreamEventsSucceed(position, true)

      connection.expectMsg(subscribeTo)
      actor ! subscribeToStreamCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event2)
      expectEvent(event2)

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

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber(number), mock[EventData])

    def readStreamEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def readStreamEventsSucceed(next: Int, endOfStream: Boolean, events: Event*) =
      ReadStreamEventsSucceed(
        events = events,
        nextEventNumber = EventNumber(next),
        lastEventNumber = mock[EventNumber.Exact],
        endOfStream = endOfStream,
        lastCommitPosition = next /*TODO*/ ,
        direction = Forward)


    def expectNoActivity {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    // TODO
    def expectEvent(x: Event) {
      expectMsg(x)
    }

    def streamEventAppeared(x: Event) = StreamEventAppeared(IndexedEvent(x, Position(x.number.value)))

    def subscribeToStreamCompleted(x: Int) = SubscribeToStreamCompleted(x, Some(EventNumber(x)))
  }
}
