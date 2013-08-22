package eventstore

import akka.testkit.{TestKitBase, TestProbe, TestActorRef}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.annotation.tailrec


/**
 * @author Yaroslav Klymko
 */
class SubscribeToAllCatchingUpSpec extends TestConnectionSpec {
  sequential

  "subscribe to all catching up" should {
    "call dropped callback after stop method call" in new SubscribeToAllCatchingUpScope {
      val subscriptionActor = newSubscription()

      subscriptionActor ! StopSubscription
      fishForMessage() {
        case _: ResolvedEvent => false
        case SubscriptionDropped(SubscriptionDropped.Unsubscribed) => true
      }
      expectNoEvents()
    }

    /*"be able to subscribe to empty db" in {
    todo how to setup empty db ???
                var subscription = store.SubscribeToAllFrom(null,
                                                            false,
                                                            ( , x) =>
                                                            {
                                                                if (!SystemStreams.IsSystemStream(x.OriginalEvent.EventStreamId))
                                                                    appeared.Set();
                                                            },
                                                              => Log.Info("Live processing started."),
                                                            ( ,   ,    ) => dropped.Signal());

                Thread.Sleep(100); // give time for first pull phase
                store.SubscribeToAll(false, (s, x) => { }, (s, r, e) => { });
                Thread.Sleep(100);

                Assert.IsFalse(appeared.Wait(0), "Some event appeared!");
                Assert.IsFalse(dropped.Wait(0), "Subscription was dropped prematurely.");
                subscription.Stop(Timeout);
                Assert.IsTrue(dropped.Wait(Timeout));
    } */

    "read all existing events and keep listening to new ones" in new SubscribeToAllCatchingUpScope {
      write()

      val existing = readAllEventsSucceed(Position.First, 1000)(ReadDirection.Backward)
        .resolvedEvents.map(_.eventRecord.event).reverse
      val subscriptionActor = newSubscription()
      expectEventsAppeared(existing)

      fishForLiveProcessingStarted()

      val newEvents = writeAsync()
      expectEventsAppeared(newEvents)

      subscriptionActor ! StopSubscription
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "filter events and keep listening to new ones" in new SubscribeToAllCatchingUpScope {
      val many = 100
      write(many)
      val position = readAllEventsSucceed(Position.First, many)(ReadDirection.Forward).position
      val subscriptionActor = newSubscription(Some(position))

      val processingStartedPosition = fishForLiveProcessingStarted(position)

      val events = writeAsync()
      expectEventsAppeared(events, processingStartedPosition)

      subscriptionActor ! StopSubscription
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "filter events and work if nothing was written after subscription" in new SubscribeToAllCatchingUpScope {
      val many = 100
      write(many)
      val position = readAllEventsSucceed(Position.First, many)(ReadDirection.Forward).position
      val subscriptionActor = newSubscription(Some(position))

      fishForLiveProcessingStarted(position)
      expectNoEvents()

      subscriptionActor ! StopSubscription
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "resolveLinkTos = true" in todo
  }

  trait SubscribeToAllCatchingUpScope extends TestConnectionScope {

    def newSubscription(fromPositionExclusive: Option[Position.Exact] = None) =
      TestActorRef(new CatchUpSubscriptionActor(actor, testActor, fromPositionExclusive, false, 500))

    def expectEventsAppeared(events: Seq[Event], position: Position = Position.First): Seq[ResolvedEvent] = {

      def loop(events: List[Event], position: Position): List[ResolvedEvent] = events match {
        case Nil => Nil
        case head :: tail =>
          val resolvedEvent = expectMsgType[ResolvedEvent]
          resolvedEvent.position must beGreaterThanOrEqualTo(position)
          resolvedEvent.link must beEmpty
          if (resolvedEvent.eventRecord.event == head) resolvedEvent :: loop(tail, resolvedEvent.position)
          else loop(events, resolvedEvent.position)
      }
      loop(events.toList, position)
    }

    @tailrec final def expectNoEvents() {
      receiveOne(FiniteDuration(1, SECONDS)) match {
        case null =>
        case msg =>
          msg must beAnInstanceOf[ResolvedEvent]
          val resolvedEvent = msg.asInstanceOf[ResolvedEvent]
          val streamId = resolvedEvent.eventRecord.streamId
          streamId.isSystem must beTrue
          expectNoEvents()
      }
    }

    @tailrec final def fishForLiveProcessingStarted(position: Position = Position.First): Position = {
      expectMsgType[AnyRef] match {
        case LiveProcessingStarted => position
        case x: ResolvedEvent =>
          x.position must beGreaterThanOrEqualTo(position)
          fishForLiveProcessingStarted(x.position)
      }
    }

    def write(size: Int = 20, streamId: EventStream.Id = streamId, testKit: TestKitBase = this): Seq[Event] = {
      val events = (1 to size).map(_ => newEvent)
      appendToStreamSucceed(events, streamId = streamId, testKit = testKit)
      events
    }

    def writeAsync(size: Int = 20): Seq[Event] = {
      val events = (1 to size).map(_ => newEvent)
      import system.dispatcher
      Future {
        appendToStreamSucceed(events, testKit = TestProbe())
      }
      events
    }
  }
}