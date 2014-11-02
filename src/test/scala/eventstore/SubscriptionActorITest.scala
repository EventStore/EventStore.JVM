package eventstore

import akka.actor.Status
import scala.concurrent.duration._

class SubscriptionActorITest extends AbstractSubscriptionActorITest {
  "SubscriptionActor" should {
    "subscribe from the beginning" in new SubscriptionScope {
      expectEvent.event.number mustEqual EventNumber.First
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
      fishForLiveProcessingStarted()
      write()
      expectEvent
    }

    "send failure if connection stopped" in new SubscriptionScope {
      system stop connection
      fishForMessage(30.seconds) {
        case LiveProcessingStarted                                  => false
        case _: IndexedEvent                                        => false
        case Status.Failure(EsException(EsError.ConnectionLost, _)) => true
      }
    }.pendingUntilFixed()

    /*"subscribe from the concrete position" in new SubscriptionScope {
      def position = Position(2)
      println(expectMsgType[Any])
      expectMsgType[IndexedEvent].position must beGreaterThan(position)

      override def fromPositionExclusive = Some(position)
    }*/
  }

  trait SubscriptionScope extends TestScope {
    val subscription = system.actorOf(SubscriptionActor.props(connection, testActor, fromPositionExclusive))

    def fishForLiveProcessingStarted(): Unit = {
      fishForMessage(30.seconds) {
        case LiveProcessingStarted => true
        case _: IndexedEvent       => false
      }
    }

    def expectEvent = expectMsgType[IndexedEvent]

    def fromPositionExclusive: Option[Position] = None
  }
}