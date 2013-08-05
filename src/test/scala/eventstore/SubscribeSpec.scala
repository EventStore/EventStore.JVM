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
      subscribeToStream().lastEventNumber mustEqual EventNumber.Max // TODO WHY?
    }

    "be able to subscribe to non existing stream and then catch new event" in new SubscribeScope {
      val subscribed = subscribeToStream()
      subscribed.lastEventNumber mustEqual EventNumber.NoEvent

      val events = appendMany(testKit = TestProbe())
      events.zipWithIndex.foreach {
        case (event, index) =>
          val resolvedEvent = expectEventAppeared()
          resolvedEvent.position.commitPosition must >(subscribed.lastCommitPosition)
          resolvedEvent.eventRecord mustEqual EventRecord(streamId, EventNumber.Exact(index), event)
      }
    }

    "allow multiple subscriptions to the same stream" in new SubscribeScope {
      subscribeToStream(TestProbe()).lastEventNumber mustEqual EventNumber.NoEvent
      subscribeToStream(TestProbe()).lastEventNumber mustEqual EventNumber.NoEvent
    }

    "be able to unsubscribe from existing stream" in new SubscribeScope {
      appendEventToCreateStream()
      subscribeToStream().lastEventNumber mustEqual EventNumber.First
      unsubscribeFromStream()
    }

    "be able to unsubscribe from not existing stream" in new SubscribeScope {
      subscribeToStream().lastEventNumber mustEqual EventNumber.NoEvent
      unsubscribeFromStream()
    }

    "catch stream deleted events" in new SubscribeScope {
      val subscribed = subscribeToStream()
      subscribed.lastEventNumber mustEqual EventNumber.NoEvent
      appendEventToCreateStream()
      expectEventAppeared().eventRecord.number mustEqual EventNumber.First
      deleteStream()
      val resolvedEvent = expectEventAppeared()
      val eventRecord = resolvedEvent.eventRecord
      resolvedEvent.position.commitPosition must >(subscribed.lastCommitPosition)
      eventRecord.number mustEqual EventNumber.Max
      eventRecord.event must beLike {
        case Event.StreamDeleted(_) => ok
      }
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream(testKit: TestKitBase = this): SubscribeToStreamCompleted = {
      actor.!(SubscribeTo(streamId, resolveLinkTos = false))(testKit.testActor)
      testKit.expectMsgType[SubscribeToStreamCompleted]
    }

    def unsubscribeFromStream() {
      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }
  }
}
