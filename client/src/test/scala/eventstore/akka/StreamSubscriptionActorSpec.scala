package eventstore
package akka

import _root_.akka.actor.ActorRef
import _root_.akka.testkit.{TestActorRef, TestProbe}
import ReadDirection.Forward

class StreamSubscriptionActorSpec extends AbstractSubscriptionActorSpec {

  "catch up subscription actor" should {

    "read events from given position" in new SubscriptionScope {
      connection expectMsg readEvents(123)

      override def eventNumber = Some(EventNumber(123))
    }

    "read events from start if no position given" in new SubscriptionScope {
      connection expectMsg readEvents(0)
    }

    "subscribe if last position given" in new SubscriptionScope {
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      connection.expectNoMessage()
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event0)
      connection reply streamEventAppeared(event2)
      expectMsg(LiveProcessingStarted)
      expectEvent(event1)
      expectEvent(event2)

      override def eventNumber = Some(EventNumber.Last)
    }

    "ignore read events with event number out of interest" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      connection reply readCompleted(3, false, event0, event1, event2)
      expectEvent(event0)
      expectEvent(event1)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply readCompleted(5, false, event0, event1, event2, event3, event4)

      expectEvent(event3)
      expectEvent(event4)

      connection expectMsg readEvents(5)

      connection reply readCompleted(5, false, event0, event1, event2, event3, event4)

      expectNoMessage(duration)
      connection expectMsg readEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new SubscriptionScope {
      connection expectMsg readEvents(1)

      connection reply readCompleted(3, false, event0, event1, event2)
      expectEvent(event2)
      expectNoMessage(duration)

      connection expectMsg readEvents(3)

      override def eventNumber = Some(EventNumber(1))
    }

    "read events until none left and subscribe to new ones" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(2, false, event1)

      expectEvent(event1)

      connection expectMsg readEvents(2)
      connection reply readCompleted(2, endOfStream = true)

      connection expectMsg subscribeTo
    }

    "subscribe to new events if nothing to read" in new SubscriptionScope {

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events if actor stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      system stop actor
      expectActorTerminated()
    }

    "catch events that appear in between reading and subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      val position = 1L
      connection reply readCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)
      connection reply readCompleted(2, endOfStream = true)

      expectNoMessage(duration)
      connection expectMsg subscribeTo

      connection reply subscribeCompleted(4)

      connection expectMsg readEvents(1) // Design Choice in SourceStageLogic that is internal (catchup)

      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      connection reply streamEventAppeared(event4)
      expectNoMessage(duration)

      connection reply readCompleted(3, false, event1, event2)
      expectEvent(event2)

      connection expectMsg readEvents(3)

      connection reply streamEventAppeared(event5)
      connection reply streamEventAppeared(event6)
      expectNoMessage(duration)

      connection reply readCompleted(6, false, event3, event4, event5)

      expectEvent(event3)
      expectEvent(event4)
      expectMsg(LiveProcessingStarted)
      expectEvent(event5)
      expectEvent(event6)

      connection reply streamEventAppeared(event5)
      connection reply streamEventAppeared(event6)

      expectNoActivity()
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      system stop actor
      expectActorTerminated()
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new SubscriptionScope {
      connection expectMsg readEvents(0)

      val position = 1L
      connection reply readCompleted(2, false, event0, event1)

      expectEvent(event0)
      expectEvent(event1)

      connection expectMsg readEvents(2)

      connection reply readCompleted(2, endOfStream = true)

      expectNoMessage(duration)
      connection expectMsg subscribeTo

      connection reply subscribeCompleted(5)

      connection expectMsg readEvents(1)  // Design Choice in SourceStageLogic that is internal (catchup)

      connection reply streamEventAppeared(event3)
      connection reply streamEventAppeared(event4)

      system stop actor
      expectActorTerminated()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new SubscriptionScope {
      val position = 0L
      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoMessage(duration)

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      expectMsg(LiveProcessingStarted)

      expectNoActivity()
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in
      new SubscriptionScope {
        val position = 1L
        connection expectMsg readEvents(position)

        connection reply readCompleted(position, endOfStream = true)

        connection expectMsg subscribeTo
        expectNoMessage(duration)

        connection reply subscribeCompleted(1)

        expectMsg(LiveProcessingStarted)

        expectNoActivity()

        override def eventNumber = Some(EventNumber(1))
      }

    "forward events while subscribed" in new SubscriptionScope {

      val position = 0L
      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      connection expectMsg subscribeTo
      expectNoMessage(duration)

      connection reply subscribeCompleted(1)

      connection expectMsg readEvents(position)
      connection reply readCompleted(position, endOfStream = true)

      expectMsg(LiveProcessingStarted)

      connection reply streamEventAppeared(event1)
      expectEvent(event1)

      expectNoMessage(duration)

      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      expectEvent(event2)
      expectEvent(event3)
    }

    "ignore wrong events while subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(1)
      connection reply readCompleted(1, endOfStream = true)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(2)

      connection expectMsg readEvents(1)
      connection reply readCompleted(1, endOfStream = true)

      expectMsg(LiveProcessingStarted)

      connection reply streamEventAppeared(event0)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      expectEvent(event2)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event3)
      expectEvent(event3)
      connection reply streamEventAppeared(event5)
      expectEvent(event5)
      connection reply streamEventAppeared(event4)
      expectNoMessage(duration)

      override def eventNumber = Some(EventNumber(1))
    }

    "stop actor if connection stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectTerminated(actor)
    }

    "stop actor if connection stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      system stop connection.ref
      expectActorTerminated()
    }

    "stop actor if client stopped" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      expectActorTerminated(probe)
    }

    "not stop subscription if actor stopped and not yet subscribed" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      system stop actor
      expectActorTerminated()
    }

    "stop actor if error while reading" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      expectTerminatedOnFailure()
    }

    "stop actor if error while subscribing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo

      expectTerminatedOnFailure()

      override def eventNumber = Some(EventNumber(0))
    }

    "stop actor if error while catching up" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)

      expectMsg(LiveProcessingStarted)
      expectTerminatedOnFailure()

      override def eventNumber = Some(EventNumber(0))
    }

    "stop actor if error while live processing" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)

      expectMsg(LiveProcessingStarted)
      expectTerminatedOnFailure()

      override def eventNumber = Some(EventNumber(0))
    }

    "resubscribe from same position" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      expectMsg(LiveProcessingStarted)
      connection reply subscribeCompleted(0)
      expectNoActivity()

      override def eventNumber = Some(EventNumber(0))
    }

    "resubscribe from different position" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(0)
      expectMsg(LiveProcessingStarted)
      connection reply subscribeCompleted(1)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      connection expectMsg readEvents(0)
      connection reply readCompleted(3, false, event0, event1, event2)
      expectEvent(event1)
      expectMsg(LiveProcessingStarted)
      expectEvent(event2)

      override def eventNumber = Some(EventNumber(0))
    }

    "resubscribe from different position while catching up" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(1, endOfStream = true, event0)

      connection expectMsg subscribeTo
      connection reply subscribeCompleted(3) // Changed in order to remain in catching up -- works in original as well
      connection reply streamEventAppeared(event3)

      connection expectMsg readEvents(0) // Design Choice in SourceStageLogic that is internal (catchup)
      connection reply readCompleted(3, false, event1, event2)

      connection reply subscribeCompleted(5)
      connection reply streamEventAppeared(event6)

      connection expectMsg readEvents(3)
      connection reply readCompleted(5, false, event3, event4)

      connection expectMsg readEvents(5)
      connection reply readCompleted(7, false, event5, event6)

      expectEvent(event1)
      expectEvent(event2)
      expectEvent(event3)
      expectEvent(event4)
      expectEvent(event5)
      expectMsg(LiveProcessingStarted)
      expectEvent(event6)

      override def eventNumber = Some(EventNumber(0))
    }

    "resubscribe while catching up" in new SubscriptionScope {
      connection expectMsg readEvents(0)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo
      connection reply subscribeCompleted(1)
      connection expectMsg readEvents(0)
      connection reply streamEventAppeared(event0)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      connection reply subscribeCompleted(2)
      connection reply streamEventAppeared(event1)
      connection reply streamEventAppeared(event2)
      connection reply streamEventAppeared(event3)
      connection reply readCompleted(0, true, event0, event1, event2)

      expectEvent(event1)
      expectEvent(event2)
      expectMsg(LiveProcessingStarted)
      expectEvent(event3)

      override def eventNumber = Some(EventNumber(0))
    }

    "use credentials if given" in new SubscriptionScope {
      connection expectMsg readEvents(0).withCredentials(credentials.get)
      connection reply readCompleted(0, endOfStream = true)
      connection expectMsg subscribeTo.withCredentials(credentials.get)

      override def credentials = Some(UserCredentials("login", "password"))
    }
  }

  trait SubscriptionScope extends AbstractScope {

    private lazy val streamPrefix: String = StreamSubscriptionActorSpec.this.getClass.getSimpleName
    lazy val streamId: EventStream.Id     = EventStream.Id(s"$streamPrefix-$randomUuid")
    def eventNumber: Option[EventNumber]  = None

    def createActor(): ActorRef = TestActorRef(StreamSubscriptionActor.props(
      connection.ref, testActor, streamId, eventNumber, credentials, settings
    ))

    val event0: Event = newEvent(0)
    val event1: Event = newEvent(1)
    val event2: Event = newEvent(2)
    val event3: Event = newEvent(3)
    val event4: Event = newEvent(4)
    val event5: Event = newEvent(5)
    val event6: Event = newEvent(6)

    def exact(value: Long): EventNumber.Exact                   = EventNumber.Exact(value)
    def expectEvent(x: Event): Event                            = expectMsg(x)
    def newEvent(number: Long): Event                           = EventRecord(streamId, exact(number),TestData.eventData)
    def subscribeCompleted(x: Long): SubscribeToStreamCompleted = SubscribeToStreamCompleted(x.toLong, Some(exact(x)))

    def readEvents(x: Long): ReadStreamEvents =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readCompleted(next: Long, endOfStream: Boolean, events: Event*): ReadStreamEventsCompleted =
      ReadStreamEventsCompleted(events.toList, EventNumber(next), exact(1L), endOfStream, next.toLong, Forward)
  }
}