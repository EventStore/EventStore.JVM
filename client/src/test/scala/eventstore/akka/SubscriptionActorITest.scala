package eventstore
package akka

import scala.concurrent.duration._
import _root_.akka.actor.Terminated
import _root_.akka.testkit.TestProbe

class SubscriptionActorITest extends AbstractSubscriptionActorITest {

  "SubscriptionActor" should {

    "subscribe from the beginning" in new SubscriptionScope {
      expectEvent.event.number mustEqual EventNumber.First
    }

    "subscribe from the concrete position" in new TestScope {
      connection ! ReadAllEvents(Position.Last, 2, ReadDirection.Backward)
      val position = expectMsgType[ReadAllEventsCompleted].events.last.position
      val subscription = system.actorOf(SubscriptionActor.props(connection, testActor, Some(position), None, settings))

      expectMsgType[IndexedEvent].position must beGreaterThan(position)
    }

    "read and then subscribe" in new SubscriptionScope {
      expectEvent
      fishForLiveProcessingStarted()
      write()
      expectEvent
    }

    "survive reconnection" in new SubscriptionScope {
      expectEvent
      reconnect()
      expectEvent
      fishForLiveProcessingStarted()
    }

    "survive reconnection after live processing started" in new SubscriptionScope {
      fishForLiveProcessingStarted()
      reconnect()
      write()
      expectEvent
    }

    "die if connection stopped" in new SubscriptionScope {
      val probe = TestProbe()
      probe watch subscription
      system stop connection
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }

    "die if client stopped" in new SubscriptionScope {
      val probe = TestProbe()
      probe watch subscription
      system stop testActor
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }
  }

  trait SubscriptionScope extends TestScope {
    val duration = 10.seconds
    val subscription = system.actorOf(SubscriptionActor.props(connection, testActor, fromPositionExclusive, None, settings))

    def fishForLiveProcessingStarted(): Unit = {
      fishForMessage(duration) {
        case LiveProcessingStarted => true
        case _: IndexedEvent       => false
      }
    }

    def expectEvent = expectMsgType[IndexedEvent]

    def fromPositionExclusive: Option[Position] = None
  }
}