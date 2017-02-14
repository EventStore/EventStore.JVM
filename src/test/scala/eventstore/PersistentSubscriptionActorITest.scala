package eventstore

import akka.actor.{ ActorRef, Terminated }
import akka.testkit.TestProbe
import eventstore.{ PersistentSubscription => PS }

import scala.concurrent.duration._

class PersistentSubscriptionActorITest extends AbstractSubscriptionActorITest {
  "PersistentSubscriptionActor" should {
    "subscribe to empty stream" in new CreateBeforeSubscriptionScope {
      subscribe
      expectLiveProcessingStarted
    }

    "subscribe from the beginning" in new CreateBeforeSubscriptionScope {
      write()
      subscribe
      expectEvent.number mustEqual EventNumber.First
      expectLiveProcessingStarted
    }

    "survive reconnect" in new CreateBeforeSubscriptionScope {
      write(3)
      subscribe
      expectEvent
      expectEvent
      expectEvent
      reconnect()
      fishForLiveProcessingStarted()
    }

    "should read events written before subscription" in new CreateBeforeSubscriptionScope {
      write(5)

      subscribe
      expectEvent
    }

    "subscribe and then read" in new CreateBeforeSubscriptionScope {
      write()
      subscribe
      expectEvent
      expectLiveProcessingStarted
      write()
      expectEvent
    }

    "receive events from after having subscribed" in new CreateBeforeSubscriptionScope {
      write(2)
      subscribe
      expectEvent
      expectEvent
      expectLiveProcessingStarted
      write()
    }

    "survive reconnect after liveProcessing has already started" in new CreateBeforeSubscriptionScope {
      subscribe
      expectLiveProcessingStarted
      reconnect()
      write()
      expectEvent
    }

    "die if connection stopped" in new CreateBeforeSubscriptionScope {
      val subscription = subscribe
      val probe = TestProbe()
      probe watch subscription
      system stop connection
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }

    "die if client stopped" in new CreateBeforeSubscriptionScope {
      val subscription = subscribe
      val probe = TestProbe()
      probe watch subscription
      system stop testActor
      probe.expectMsgPF() { case Terminated(`subscription`) => () }
    }

    "read from in subscription settings" in new PersistentSubscriptionScope {
      write(3)
      create(settings = PersistentSubscriptionSettings.Default.copy(startFrom = EventNumber(1)))
      expectCreated
      subscribe
      expectEvent.number.mustEqual(EventNumber.Exact(1))
    }
  }

  trait PersistentSubscriptionScope extends TestScope {
    val groupName: String = randomUuid.toString

    def create(settings: PersistentSubscriptionSettings = PersistentSubscriptionSettings.Default): Unit = connection ! PS.Create(EventStream.Id(streamId.streamId), groupName, settings)

    def subscribe: ActorRef = system.actorOf(PersistentSubscriptionActor.props(
      connection,
      testActor,
      streamId,
      groupName,
      None,
      settings,
      autoAck = true
    ))

    def fishForLiveProcessingStarted(): Unit = {
      fishForMessage(5.seconds) {
        case LiveProcessingStarted => true
        case _: Event              => false
      }
    }

    def expectEvent: Event = expectMsgType[Event]

    def expectCreated: PS.CreateCompleted.type = expectMsg(PS.CreateCompleted)
  }

  trait CreateBeforeSubscriptionScope extends PersistentSubscriptionScope {
    create()
    expectCreated
  }
}
