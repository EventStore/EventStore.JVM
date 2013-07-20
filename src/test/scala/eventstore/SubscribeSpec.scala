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
      subscribeToStream()
    }

    "be able to subscribe to non existing stream and then catch new event" in new SubscribeScope {
      subscribeToStream()

      val events = appendMany(TestProbe())

      val er = eventRecord(0, events.head)
      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(`er`, None, _, _)) =>
      }
    }

    "allow multiple subscriptions to the same stream" in new SubscribeScope {
      subscribeToStream(TestProbe())
      subscribeToStream(TestProbe())
    }

    "be able to unsubscribe from existing stream" in new SubscribeScope {
      appendEventToCreateStream()
      subscribeToStream()
      unsubscribeFromStream()
    }

    "be able to unsubscribe from not existing stream" in new SubscribeScope {
      subscribeToStream()
      unsubscribeFromStream()
    }

    "catch stream deleted events" in new SubscribeScope {
      subscribeToStream()
      appendEventToCreateStream()
      expectMsgType[StreamEventAppeared]
      deleteStream()
      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, _, Event.StreamDeleted(_)), None, _, _)) => true
      }
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream(testKit: TestKitBase = this) {
      actor.!(SubscribeToStream(streamId, resolveLinkTos = false))(testKit.testActor)
      testKit.expectMsgType[SubscriptionConfirmation]
    }

    def unsubscribeFromStream() {
      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped(SubscriptionDropped.Reason.Unsubscribed))
    }
  }
}
