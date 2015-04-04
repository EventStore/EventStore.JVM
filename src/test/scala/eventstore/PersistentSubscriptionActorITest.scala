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
    }

    "survive reconnect" in new CreateBeforeSubscriptionScope {
      write(100)
      subscribe
      reconnect()
      fishForLiveProcessingStarted()
    }

    "should read from current" in new CreateBeforeSubscriptionScope {
      write(5)

      override def fromNumberExclusive = Some(EventNumber.Current)
      subscribe
      expectLiveProcessingStarted
      expectNoMsg()
    }

    "read and then subscribe" in new CreateBeforeSubscriptionScope {
      write()
      subscribe
      expectEvent
      expectLiveProcessingStarted
      write()
      expectEvent
    }

    "receive events from after having subscribed" in new CreateBeforeSubscriptionScope {
      write(5)

      override def fromNumberExclusive: Option[EventNumber] = Some(EventNumber.Current)

      subscribe
      expectLiveProcessingStarted
      write()
      expectEvent.number mustEqual EventNumber.Exact(5)
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
  }

  trait PersistentSubscriptionScope extends TestScope {
    val groupName: String = randomUuid.toString

    def create(settings: PersistentSubscriptionSettings = PersistentSubscriptionSettings.Default): Unit = connection ! PS.Create(EventStream.Id(streamId.streamId), groupName, settings)

    def subscribe: ActorRef = system.actorOf(PersistentSubscriptionActor.props(
      connection,
      testActor,
      streamId,
      groupName,
      fromNumberExclusive,
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

    def fromNumberExclusive: Option[EventNumber] = None
  }

  trait CreateBeforeSubscriptionScope extends PersistentSubscriptionScope {
    create()
    expectCreated
  }
}
