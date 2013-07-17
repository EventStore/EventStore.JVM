package eventstore

import OperationResult._
import eventstore.tcp.UuidSerializer
import akka.testkit.TestProbe

/**
 * @author Yaroslav Klymko
 */
class SubscribeSpec extends TestConnectionSpec {
  "subscribe" should {
    "succeed for existing stream" in new SubscribeScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]
    }

    "succeed for not existing stream" in new SubscribeScope {
      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]
    }

    "succeed for deleted stream but should not receive any events" in new SubscribeScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]

      expectNoMsg()
    }

    "be able to subscribe to non existing stream and then catch new event" in new SubscribeScope {
      actor ! subscribeToStream
      //      expectMsg(SubscriptionConfirmation(0, None))
      // TODO why SubscriptionConfirmation(94956,Some(-1)) ?

      expectMsgType[SubscriptionConfirmation]

      val event = newEvent

      val probe = TestProbe()
      actor.receive(WriteEvents(streamId, AnyVersion, List(event), requireMaster = true), probe.ref)
      probe.expectMsg(WriteEventsCompleted(Success, None, 0))

      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, 0, _, "$stream-created-implicit", ByteString.empty, Some(ByteString.empty)), None, _, _)) => true
      }

      val er = eventRecord(1, event)
      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(`er`, None, _, _)) =>
      }
    }

    "allow multiple subscriptions to the same stream" in new SubscribeScope {
      val client1 = TestProbe()
      actor.!(subscribeToStream)(client1.ref)
      client1.expectMsgType[SubscriptionConfirmation]

      val client2 = TestProbe()
      actor.!(subscribeToStream)(client2.ref)
      client2.expectMsgType[SubscriptionConfirmation]
    }
    "be able to unsubscribe from existing stream" in new SubscribeScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]

      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped)
    }

    "be able to unsubscribe from not existing stream" in new SubscribeScope {
      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]

      actor ! UnsubscribeFromStream
      expectMsg(SubscriptionDropped)
    }

    "catch created and deleted but not dropped" in new SubscribeScope {
      actor ! subscribeToStream
      expectMsgType[SubscriptionConfirmation]

      val probe = TestProbe()
      actor.!(createStream)(probe.ref)
      probe.expectMsg(createStreamCompleted)

      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, 0, _, "$stream-created", ByteString.empty, Some(_)), None, _, _)) =>
      }

      actor.!(deleteStream())(probe.ref)
      probe.expectMsg(deleteStreamCompleted)

      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, _, _, "$stream-deleted", ByteString.empty, Some(ByteString.empty)), None, _, _)) => true
      }

      expectNoMsg()
    }
  }

  trait SubscribeScope extends TestConnectionScope {
    def subscribeToStream = SubscribeToStream(streamId, resolveLinkTos = false)
  }
}
