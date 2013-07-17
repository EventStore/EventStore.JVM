package eventstore

import akka.testkit.TestProbe
import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class SubscribeToAllSpec extends TestConnectionSpec {
  sequential

  "subscribe to all" should {
    "allow multiple subscriptions" in new SubscribeToAll {
      val probe = TestProbe()
      val event = newEvent

      actor.!(createStream)(probe.ref)
      probe.expectMsg(createStreamCompleted)

      actor ! SubscribeToStream("", resolveLinkTos = false)
      expectMsgType[SubscriptionConfirmation]

      actor.!(writeEvents(AnyVersion, event))(probe.ref)
      probe.expectMsgPF() {
        case WriteEventsCompleted(Success, None, _) => true
      }

      val er = eventRecord(1, event)
      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(`er`, _, _, _)) => true
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      actor ! SubscribeToStream("", resolveLinkTos = false)
      expectMsgType[SubscriptionConfirmation]

      val probe = TestProbe()
      actor.!(createStream)(probe.ref)
      probe.expectMsg(createStreamCompleted)

//      StreamEventAppeared(ResolvedEvent(EventRecord(SubscribeToAllSpec-26f49c3a-4f85-4307-8531-47ad5bcf5b7a,0,ByteString(1, 71, -47, 37, -73, -58, 81, 85, 107, -11, 54, 95, -61, -34, -126, -123),Some($stream-created),ByteString(),Some(ByteString(83, 117, 98, 115, 99, 114, 105, 98, 101, 84, 111, 65, 108, 108, 83, 112, 101, 99))),None,835849,835675))
      println(expectMsgType[StreamEventAppeared])

      actor.!(deleteStream())(probe.ref)
      probe.expectMsg(deleteStreamCompleted)

      //StreamEventAppeared(ResolvedEvent(EventRecord(SubscribeToAllSpec-26f49c3a-4f85-4307-8531-47ad5bcf5b7a,2147483647,ByteString(109, -26, -88, 96, 78, 76, 60, 75, -102, 3, -118, -41, 14, -3, -31, 110),Some($stream-deleted),ByteString(),Some(ByteString())),None,836067,835911))subscribe to all should
      println(expectMsgType[StreamEventAppeared])
    }
  }

  trait SubscribeToAll extends TestConnectionScope
}
