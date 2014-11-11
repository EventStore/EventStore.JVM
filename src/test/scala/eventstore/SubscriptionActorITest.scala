package eventstore

import akka.actor.Status
import scala.concurrent.duration._

class SubscriptionActorITest extends AbstractSubscriptionActorITest {
  "SubscriptionActor" should {
    "subscribe from the beginning" in new SubscriptionScope {
      expectEvent.event.number mustEqual EventNumber.First
    }

    "subscribe from the concrete position" in new TestScope {
      connection ! ReadAllEvents(Position.Last, 2, ReadDirection.Backward)
      val position = expectMsgType[ReadAllEventsCompleted].events.last.position
      val subscription = system.actorOf(SubscriptionActor.props(connection, testActor, Some(position)))

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

    "send failure if connection stopped" in new SubscriptionScope {
      system stop connection
      fishForMessage(duration) {
        case LiveProcessingStarted                                  => false
        case _: IndexedEvent                                        => false
        case Status.Failure(EsException(EsError.ConnectionLost, _)) => true
      }
    }.pendingUntilFixed()
  }

  trait SubscriptionScope extends TestScope {
    val duration = 10.seconds
    val subscription = system.actorOf(SubscriptionActor.props(connection, testActor, fromPositionExclusive))

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