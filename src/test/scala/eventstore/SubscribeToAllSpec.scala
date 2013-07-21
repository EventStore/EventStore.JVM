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
      appendEventToCreateStream()

      val clients = List(TestProbe(), TestProbe(), TestProbe())
      clients.foreach(subscribeToAll(_))

      val event = newEvent
      doAppendToStream(event, AnyVersion, 1)

      clients.foreach {
        client => expectEventAppeared(EventNumber.Exact(1), client) mustEqual event
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      subscribeToAll()
      appendEventToCreateStream()
      expectEventAppeared(EventNumber.First)
      deleteStream()
      expectEventAppeared(EventNumber.Max) must beLike {
        case Event.StreamDeleted(_) => ok
      }
    }
  }

  trait SubscribeToAll extends TestConnectionScope {
    def subscribeToAll(testKit: TestKitBase = this) {
      actor.!(SubscribeTo(EventStream.All))(testKit.testActor)
      testKit.expectMsgType[SubscribeToAllCompleted]
    }
  }
}
