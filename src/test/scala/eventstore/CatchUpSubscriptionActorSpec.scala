package eventstore

import org.specs2.mutable.SpecificationWithJUnit
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.actor.ActorSystem
import ReadDirection.Forward
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class CatchUpSubscriptionActorSpec extends SpecificationWithJUnit with Mockito {
  "CatchUpSubscriptionActor" should {

    "read events from given position" in new CatchUpSubscriptionActorScope(Some(Position(123))) {
      connection.expectMsgPF() {
        case ReadAllEvents(Position(123, 123), `maxCount`, _, Forward) =>
      }
    }

    "read events from start if no position given" in new CatchUpSubscriptionActorScope {
      connection.expectMsgPF() {
        case ReadAllEvents(Position.start, `maxCount`, _, Forward) =>
      }
    }

    "ignore read events with position out of interest" in new CatchUpSubscriptionActorScope {
      connection.expectMsgPF() {
        case ReadAllEvents(Position.start, `maxCount`, _, Forward) =>
      }

      val `re-1` = ResolvedEvent(mock[EventRecord], None, Position(-1))
      val re0 = ResolvedEvent(mock[EventRecord], None, Position.start)
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))
      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))

      actor ! ReadAllEventsCompleted(Position.start, List(re0, re1, re2), Position(3), Forward)
      expectMsg(re0)
      expectMsg(re1)
      expectMsg(re2)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(3, 3), `maxCount`, _, Forward) =>
      }

      val re3 = ResolvedEvent(mock[EventRecord], None, Position(3))
      val re4 = ResolvedEvent(mock[EventRecord], None, Position(4))

      actor ! ReadAllEventsCompleted(Position(3), List(re0, re1, re2, re3, re4), Position(5), Forward)

      expectMsg(re3)
      expectMsg(re4)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(5, 5), `maxCount`, _, Forward) =>
      }

      actor ! ReadAllEventsCompleted(Position(3), List(re0, re1, re2, re3, re4), Position(5), Forward)

      expectNoMsg(noMessageDuration)
      connection.expectMsgPF() {
        case ReadAllEvents(Position(5, 5), `maxCount`, _, Forward) =>
      }
    }
    "ignore read events with position out of interest when start position is given" in new CatchUpSubscriptionActorScope(Some(Position(1))) {
      connection.expectMsgPF() {
        case ReadAllEvents(Position(1, 1), `maxCount`, _, Forward) =>
      }

      val re0 = ResolvedEvent(mock[EventRecord], None, Position.start)
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))
      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))

      actor ! ReadAllEventsCompleted(Position.start, List(re0, re1, re2), Position(3), Forward)
      expectMsg(re2)
      expectNoMsg(noMessageDuration)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(3, 3), `maxCount`, _, Forward) =>
      }
    }

    "read events until none left and subscribe to new ones" in new CatchUpSubscriptionActorScope {
      connection.expectMsgType[ReadAllEvents]
      val position = Position(1)
      val nextPosition = Position(2)
      val resolvedEvent = ResolvedEvent(mock[EventRecord], None, position)
      actor ! ReadAllEventsCompleted(position, List(resolvedEvent), nextPosition, Forward)

      expectMsg(resolvedEvent)

      connection.expectMsgPF() {
        case ReadAllEvents(`nextPosition`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(nextPosition, Nil, nextPosition, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
    }

    "subscribe to new events if nothing to read" in new CatchUpSubscriptionActorScope {
      connection.expectMsgPF() {
        case ReadAllEvents(Position(0, 0), `maxCount`, _, Forward) =>
      }
      val position = Position.start
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)
      connection.expectMsg(SubscribeTo(AllStreams))

      actor ! SubscribeToAllCompleted(1)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(0, 0), `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events as soon as stop received" in new CatchUpSubscriptionActorScope {
      connection.expectMsgType[ReadAllEvents]

      actor ! StopSubscription
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "ignore read events after stop received" in new CatchUpSubscriptionActorScope {
      connection.expectMsgType[ReadAllEvents]

      actor ! StopSubscription
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      val position = Position(1)
      val resolvedEvent = ResolvedEvent(mock[EventRecord], None, position)
      actor ! ReadAllEventsCompleted(position, List(resolvedEvent), Position(2), Forward)

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "catch events that appear in between reading and subscribing" in new CatchUpSubscriptionActorScope() {
      connection.expectMsgPF() {
        case ReadAllEvents(Position.start, `maxCount`, _, Forward) =>
      }

      val re0 = ResolvedEvent(mock[EventRecord], None, Position.start)
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))

      val position = Position(1)
      actor ! ReadAllEventsCompleted(Position.start, List(re0, re1), Position(2), Forward)

      expectMsg(re0)
      expectMsg(re1)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(2, 2), `maxCount`, _, Forward) =>
      }

      actor ! ReadAllEventsCompleted(Position(2), Nil, Position(2), Forward)

      expectNoMsg(noMessageDuration)
      connection.expectMsg(SubscribeTo(AllStreams))

      actor ! SubscribeToAllCompleted(4)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(2, 2), `maxCount`, _, Forward) =>
      }

      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))
      val re3 = ResolvedEvent(mock[EventRecord], None, Position(3))
      val re4 = ResolvedEvent(mock[EventRecord], None, Position(4))

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)
      expectNoMsg(noMessageDuration)

      actor ! ReadAllEventsCompleted(Position(2), List(re1, re2), Position(3), Forward)
      expectMsg(re2)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(3, 3), `maxCount`, _, Forward) =>
      }

      val re5 = ResolvedEvent(mock[EventRecord], None, Position(5))
      val re6 = ResolvedEvent(mock[EventRecord], None, Position(6))

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)
      expectNoMsg(noMessageDuration)

      actor ! ReadAllEventsCompleted(Position(3), List(re3, re4, re5), Position(6), Forward)

      expectMsg(re3)
      expectMsg(re4)
      expectMsg(LiveProcessingStarted)
      expectMsg(re5)
      expectMsg(re6)

      actor ! StreamEventAppeared(re5)
      actor ! StreamEventAppeared(re6)

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new CatchUpSubscriptionActorScope() {
      connection.expectMsgType[ReadAllEvents]
      actor ! ReadAllEventsCompleted(Position.start, Nil, Position.start, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      actor ! StopSubscription

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)

      actor ! SubscribeToAllCompleted(1)

      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "not unsubscribe if subscription failed" in new CatchUpSubscriptionActorScope() {
      connection.expectMsgType[ReadAllEvents]
      actor ! ReadAllEventsCompleted(Position.start, Nil, Position.start, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)

      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "not unsubscribe if subscription failed if stop received " in new CatchUpSubscriptionActorScope() {
      connection.expectMsgType[ReadAllEvents]
      actor ! ReadAllEventsCompleted(Position.start, Nil, Position.start, Forward)

      actor ! StopSubscription
      connection.expectMsg(SubscribeTo(AllStreams))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)

      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)
      expectMsg(SubscriptionDropped(SubscriptionDropped.AccessDenied))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new CatchUpSubscriptionActorScope() {
      connection.expectMsgPF() {
        case ReadAllEvents(Position.start, `maxCount`, _, Forward) =>
      }

      val re0 = ResolvedEvent(mock[EventRecord], None, Position.start)
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))

      val position = Position(1)
      actor ! ReadAllEventsCompleted(Position.start, List(re0,re1), Position(2), Forward)

      expectMsg(re0)
      expectMsg(re1)

      connection.expectMsgPF() {
        case ReadAllEvents(Position(2, 2), `maxCount`, _, Forward) =>
      }

      actor ! ReadAllEventsCompleted(Position(2), Nil, Position(2), Forward)

      expectNoMsg(noMessageDuration)
      connection.expectMsg(SubscribeTo(AllStreams))

      actor ! SubscribeToAllCompleted(5)

      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))
      val re3 = ResolvedEvent(mock[EventRecord], None, Position(3))
      val re4 = ResolvedEvent(mock[EventRecord], None, Position(4))

      connection.expectMsgPF() {
        case ReadAllEvents(Position(2, 2), `maxCount`, _, Forward) =>
      }

      actor ! StreamEventAppeared(re3)
      actor ! StreamEventAppeared(re4)

      actor ! StopSubscription

      expectNoMsg(noMessageDuration)
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      connection.expectNoMsg(noMessageDuration)
      expectNoMsg(noMessageDuration)
    }

    "continue with subscription if no events appear in between reading and subscribing" in new CatchUpSubscriptionActorScope() {
      val position = Position.start
      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      expectNoMsg(noMessageDuration)

      actor ! SubscribeToAllCompleted(1)

      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      expectMsg(LiveProcessingStarted)

      connection.expectNoMsg(noMessageDuration)
      expectNoMsg(noMessageDuration)
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in new CatchUpSubscriptionActorScope(Some(Position(1))) {
      val position = Position(1)
      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }

      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      expectNoMsg(noMessageDuration)

      actor ! SubscribeToAllCompleted(1)

      expectMsg(LiveProcessingStarted)

      connection.expectNoMsg(noMessageDuration)
      expectNoMsg(noMessageDuration)
    }

    "forward events while subscribed" in new CatchUpSubscriptionActorScope() {
      val position = Position.start
      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      expectNoMsg(noMessageDuration)

      actor ! SubscribeToAllCompleted(1)

      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      expectMsg(LiveProcessingStarted)

      val re0 = ResolvedEvent(mock[EventRecord], None, Position(0))
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))
      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))
      val re3 = ResolvedEvent(mock[EventRecord], None, Position(3))
      val re4 = ResolvedEvent(mock[EventRecord], None, Position(4))

      actor ! StreamEventAppeared(re1)
      expectMsg(re1)

      expectNoMsg(noMessageDuration)

      actor ! StreamEventAppeared(re2)
      actor ! StreamEventAppeared(re3)
      expectMsg(re2)
      expectMsg(re3)
    }

    "ignore wrong events while subscribed" in new CatchUpSubscriptionActorScope(Some(Position(1))) {
      val position = Position(1)
      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      actor ! SubscribeToAllCompleted(2)

      connection.expectMsgPF() {
        case ReadAllEvents(`position`, `maxCount`, _, Forward) =>
      }
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      expectMsg(LiveProcessingStarted)

      val re0 = ResolvedEvent(mock[EventRecord], None, Position(0))
      val re1 = ResolvedEvent(mock[EventRecord], None, Position(1))
      val re2 = ResolvedEvent(mock[EventRecord], None, Position(2))
      val re3 = ResolvedEvent(mock[EventRecord], None, Position(3))
      val re4 = ResolvedEvent(mock[EventRecord], None, Position(4))
      val re5 = ResolvedEvent(mock[EventRecord], None, Position(5))

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
      expectNoMsg(noMessageDuration)
    }

    "stop subscription when stop received" in new CatchUpSubscriptionActorScope(Some(Position(1))) {
      connection.expectMsgType[ReadAllEvents]

      val position = Position(1)
      actor ! ReadAllEventsCompleted(position, Nil, position, Forward)

      connection.expectMsg(SubscribeTo(AllStreams))
      actor ! SubscribeToAllCompleted(1)
      expectMsg(LiveProcessingStarted)

      val re = ResolvedEvent(mock[EventRecord], None, Position(2))
      actor ! StreamEventAppeared(re)
      expectMsg(re)

      actor ! StopSubscription
      connection.expectMsg(UnsubscribeFromStream)

      actor ! SubscriptionDropped(SubscriptionDropped.Unsubscribed)
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))

      expectNoMsg(noMessageDuration)
      connection.expectNoMsg(noMessageDuration)
    }
  }

  abstract class CatchUpSubscriptionActorScope(position: Option[Position] = None)
    extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val noMessageDuration = FiniteDuration(1, SECONDS)
    val maxCount = 500
    val connection = TestProbe()
    val actor = TestActorRef(new CatchUpSubscriptionActor(connection.ref, testActor, position, false, None))
  }
}
