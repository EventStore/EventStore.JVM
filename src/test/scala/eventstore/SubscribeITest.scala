package eventstore

import akka.testkit.{ TestKitBase, TestProbe }
import scala.concurrent.duration._

class SubscribeITest extends TestConnection {
  "subscribe" should {

    "be able to subscribe" in new SubscribeScope {
      append(newEventData)
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beSome(EventNumber(0))
      append(newEventData)
      subscribeToStream(testKit = TestProbe()).lastEventNumber must beSome(EventNumber(1))
      append(newEventData)
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
          val indexedEvent = expectStreamEventAppeared()
          indexedEvent.position.commitPosition must >(subscribed.lastCommit)
          indexedEvent.event mustEqual EventRecord(streamId, EventNumber(index), event)
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
      expectStreamEventAppeared().event.number mustEqual EventNumber.First
      deleteStream()
      val indexedEvent = expectStreamEventAppeared()
      indexedEvent.position.commitPosition must >(subscribed.lastCommit)
      indexedEvent.event must beLike {
        case Event.StreamDeleted(`streamId`, _) => ok
      }
      expectNoMsg(1.second)
    }

    "not catch linked events if resolveLinkTos = false" in new SubscribeScope {
      subscribeToStream(resolveLinkTos = false)
      val (linked, link) = linkedAndLink()
      expectStreamEventAppeared().event mustEqual linked
      expectStreamEventAppeared()
      expectStreamEventAppeared().event mustEqual link
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeScope {
      subscribeToStream(resolveLinkTos = true)
      val (linked, link) = linkedAndLink()
      expectStreamEventAppeared().event mustEqual linked
      expectStreamEventAppeared()
      expectStreamEventAppeared().event mustEqual ResolvedEvent(linked, link)
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream(resolveLinkTos: Boolean = false, testKit: TestKitBase = this): SubscribeToStreamCompleted = {
      actor.!(SubscribeTo(streamId, resolveLinkTos = resolveLinkTos))(testKit.testActor)
      testKit.expectMsgType[SubscribeToStreamCompleted]
    }

    def unsubscribeFromStream() {
      actor ! Unsubscribe
      expectMsg(UnsubscribeCompleted)
    }
  }
}