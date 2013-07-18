package eventstore

import akka.testkit.{TestKitBase, TestProbe}
import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class SubscribeToAllSpec extends TestConnectionSpec {
  sequential

  "subscribe to all" should {
    "allow multiple subscriptions" in new SubscribeToAll {
      createStream()

      val clients = List(TestProbe(), TestProbe(), TestProbe())
      clients.foreach(subscribeToAll(_))

      val event = newEvent
      doAppendToStream(event, AnyVersion, 1)

      val er = eventRecord(1, event)

      clients.foreach {
        client => client.expectMsgPF() {
          case StreamEventAppeared(ResolvedEvent(`er`, _, _, _)) => true
        }
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      subscribeToAll()
      createStream()
      expectMsgType[StreamEventAppeared]
      deleteStream()
      expectMsgPF() {
        case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, _, Event.StreamDeleted(_)), None, _, _)) => true
      }
    }
  }

  trait SubscribeToAll extends TestConnectionScope {
    def subscribeToAll(testKit: TestKitBase = this) {
      actor.!(SubscribeToStream(""))(testKit.testActor) // TODO don't like passing empty string, need to create const...
      testKit.expectMsgType[SubscriptionConfirmation]
    }
  }
}
