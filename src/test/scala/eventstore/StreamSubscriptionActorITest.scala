package eventstore

import akka.actor.Terminated
import akka.testkit.TestProbe
import scala.concurrent.duration._

class StreamSubscriptionActorITest extends AbstractSubscriptionActorITest {
  "StreamSubscriptionActor" should {
    "subscribe to empty stream" in new SubscriptionScope {
      subscribe
      expectLiveProcessingStarted
    }

    "subscribe from the beginning" in new SubscriptionScope {
      write()
      subscribe
      expectEvent.number mustEqual EventNumber.First
    }

    "read not from the beginning" in new SubscriptionScope {
      write(6)
      subscribe
      expectEvent.number mustEqual EventNumber(4)
      expectEvent.number mustEqual EventNumber(5)
      expectLiveProcessingStarted

      override def fromNumberExclusive = Some(EventNumber.Exact(3))
    }

    "subscribe not from the beginning" in new SubscriptionScope {
      write()
      subscribe
      expectLiveProcessingStarted
      write(5)
      expectEvent.number mustEqual EventNumber(4)
      expectEvent.number mustEqual EventNumber(5)

      override def fromNumberExclusive = Some(EventNumber.Exact(3))
    }

    "read and then subscribe" in new SubscriptionScope {
      write()
      subscribe
      expectEvent
      expectLiveProcessingStarted
      write()
      expectEvent
    }

    "survive reconnection" in new SubscriptionScope {
      write(100)
      subscribe
      reconnect()
      fishForLiveProcessingStarted()
    }

    "survive reconnection after live processing started" in new SubscriptionScope {
      subscribe
      expectLiveProcessingStarted
      reconnect()
      write()
      expectEvent
    }

    "die if connection stopped" in new SubscriptionScope {
      val subscription = subscribe
      val probe = TestProbe()
      probe watch subscription
      system stop connection
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }

    "die if client stopped" in new SubscriptionScope {
      val subscription = subscribe
      val probe = TestProbe()
      probe watch subscription
      system stop testActor
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }
  }

  trait SubscriptionScope extends TestScope {
    def subscribe = system.actorOf(StreamSubscriptionActor.props(
      connection,
      testActor,
      streamId,
      fromNumberExclusive,
      None,
      settings
    ))

    def fishForLiveProcessingStarted(): Unit = {
      fishForMessage(5.seconds) {
        case LiveProcessingStarted => true
        case _: Event              => false
      }
    }

    def expectEvent = expectMsgType[Event]

    def fromNumberExclusive: Option[EventNumber] = None
  }
}
