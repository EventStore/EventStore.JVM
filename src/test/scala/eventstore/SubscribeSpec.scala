package eventstore

import akka.testkit.{TestKitBase, TestProbe}
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class SubscribeSpec extends TestConnectionSpec {
  "subscribe" should {

    "be able to subscribe" in new SubscribeScope {
      append(newEvent)
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beSome(EventNumber(0))
      append(newEvent)
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beSome(EventNumber(1))
      append(newEvent)
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beSome(EventNumber(2))
    }

    "succeed for deleted stream but should not receive any events" in new SubscribeScope {
      appendEventToCreateStream()
      deleteStream()
      subscribeToStream().lastEventNumber must beSome(EventNumber(Int.MaxValue)) // TODO WHY?
    }

    "be able to subscribe to non existing stream and then catch new event" in new SubscribeScope {
      val subscribed = subscribeToStream()
      subscribed.lastEventNumber mustEqual None

      val events = appendMany(testKit = TestProbe())
      events.zipWithIndex.foreach {
        case (event, index) =>
          val resolvedEvent = expectEventAppeared()
          resolvedEvent.position.commitPosition must >(subscribed.lastCommit)
          resolvedEvent.eventRecord mustEqual EventRecord(streamId, EventNumber(index), event)
      }
    }

    "allow multiple subscriptions to the same stream" in new SubscribeScope {
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beNone
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beNone
    }

    "be able to unsubscribe from existing stream" in new SubscribeScope {
      appendEventToCreateStream()
      subscribeToStream().lastEventNumber must beSome(EventNumber.First)
      unsubscribeFromStream()
    }

    "be able to unsubscribe from not existing stream" in new SubscribeScope {
      subscribeToStream().lastEventNumber must beNone
      unsubscribeFromStream()
    }

    "catch stream deleted events" in new SubscribeScope {
      val subscribed = subscribeToStream()
      subscribed.lastEventNumber must beNone
      appendEventToCreateStream()
      expectEventAppeared().eventRecord.number mustEqual EventNumber.First
      deleteStream()
      val resolvedEvent = expectEventAppeared()
      resolvedEvent.position.commitPosition must >(subscribed.lastCommit)
      resolvedEvent.eventRecord must beLike {
        case EventRecord.StreamDeleted(`streamId`, EventNumber.Exact(Int.MaxValue/*TODO WHY?*/), _) => ok
      }
      expectNoMsg(FiniteDuration(1, SECONDS))
    }

    "not catch linked events if resolveLinkTos = false" in new SubscribeScope {
      subscribeToStream(resolveLinkTos = false)
      val (linked, link) = linkedAndLink()

      expectEventAppeared()
      expectEventAppeared()
      val resolvedEvent = expectEventAppeared()
      resolvedEvent.eventRecord mustEqual link
      resolvedEvent.link must beNone
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeScope {
      subscribeToStream(resolveLinkTos = true)
      val (linked, link) = linkedAndLink()

      expectEventAppeared()
      expectEventAppeared()
      val resolvedEvent = expectEventAppeared()
      resolvedEvent.eventRecord mustEqual linked
      resolvedEvent.link must beSome(link)
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream(resolveLinkTos: Boolean = false, testKit: TestKitBase = this): SubscribeToStreamCompleted = {
      actor.!(SubscribeTo(streamId, resolveLinkTos = resolveLinkTos))(testKit.testActor)
      testKit.expectMsgType[SubscribeToStreamCompleted]
    }

    def unsubscribeFromStream() {
      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }
  }
}