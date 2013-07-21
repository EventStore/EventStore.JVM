package eventstore

import akka.testkit.{TestKitBase, TestProbe}

/**
 * @author Yaroslav Klymko
 */
class SubscribeSpec extends TestConnectionSpec {
  "subscribe" should {
    "succeed for deleted stream but should not receive any events" in new SubscribeScope {
      appendEventToCreateStream()
      deleteStream()
      subscribeToStream() mustEqual EventNumber.Max // TODO WHY?
    }

    "be able to subscribe to non existing stream and then catch new event" in new SubscribeScope {
      subscribeToStream() mustEqual EventNumber.NoEvent

      val events = appendMany(TestProbe())

      events.zipWithIndex.foreach {
        case (event, index) =>
          expectEventAppeared(EventNumber.Exact(index)) mustEqual event
      }
    }

    "allow multiple subscriptions to the same stream" in new SubscribeScope {
      subscribeToStream(TestProbe()) mustEqual EventNumber.NoEvent
      subscribeToStream(TestProbe()) mustEqual EventNumber.NoEvent
    }

    "be able to unsubscribe from existing stream" in new SubscribeScope {
      appendEventToCreateStream()
      subscribeToStream() mustEqual EventNumber.First
      unsubscribeFromStream()
    }

    "be able to unsubscribe from not existing stream" in new SubscribeScope {
      subscribeToStream() mustEqual EventNumber.NoEvent
      unsubscribeFromStream()
    }

    "catch stream deleted events" in new SubscribeScope {
      subscribeToStream() mustEqual EventNumber.NoEvent
      appendEventToCreateStream()
      expectEventAppeared(EventNumber.First)
      deleteStream()
      expectEventAppeared(EventNumber.Max) must beLike {
        case Event.StreamDeleted(_) => ok
      }
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream(testKit: TestKitBase = this): EventNumber = {
      actor.!(SubscribeTo(streamId, resolveLinkTos = false))(testKit.testActor)
      testKit.expectMsgType[SubscribeToStreamCompleted].lastEventNumber
    }

    def unsubscribeFromStream() {
      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }
  }
}
