package eventstore

import akka.testkit.{TestKitBase, TestProbe, TestActorRef}
import akka.actor.ActorRef
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.annotation.tailrec
import ReadDirection.Backward
import CatchUpSubscription._


/**
 * @author Yaroslav Klymko
 */
class SubscribeToAllCatchingUpSpec extends TestConnectionSpec {
  sequential

  "subscribe to all catching up" should {
    "call dropped callback after stop method call" in new SubscribeToAllCatchingUpScope {
      val subscriptionActor = newSubscription()

      subscriptionActor ! Stop
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
      appendMany()

      val existing = readAllEventsSucceed(Position.First, 1000)(Backward)
        .resolvedEvents.map(_.eventRecord.event).reverse
      val subscriptionActor = newSubscription()
      expectEvents(existing)

      fishForLiveProcessingStarted()

      val newEvents = writeAsync()
      expectEvents(newEvents)

      subscriptionActor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "filter events and keep listening to new ones" in new SubscribeToAllCatchingUpScope {
      val many = 10
      appendMany(many)
      val position = allStreamsResolvedEvents()(Backward).take(many).last.position
      val subscriptionActor = newSubscription(Some(position))

      val processingStartedPosition = fishForLiveProcessingStarted(position)

      val events = writeAsync()
      expectEvents(events, processingStartedPosition)

      subscriptionActor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "filter events and work if nothing was written after subscription" in new SubscribeToAllCatchingUpScope {
      val many = 10
      appendMany(many)
      val position = allStreamsResolvedEvents()(Backward).take(many).last.position
      val subscriptionActor = newSubscription(Some(position))

      fishForLiveProcessingStarted(position)
      expectNoEvents()

      subscriptionActor ! Stop
      expectMsg(SubscriptionDropped(SubscriptionDropped.Unsubscribed))
    }

    "allow multiple subscriptions" in new SubscribeToAllCatchingUpScope {
      val position = allStreamsResolvedEvents()(Backward).take(10).last.position
      val probes = List.fill(5)(TestProbe.apply)
      probes.foreach(x => newSubscription(Some(position), client = x.ref))
      probes.foreach(x => fishForLiveProcessingStarted(testKit = x))
      val event = append(newEvent).event
      probes.foreach(x => expectEvents(Seq(event), testKit = x))
    }

    "catch link events if resolveLinkTos = false" in new SubscribeToAllCatchingUpScope {
      val position = allStreamsResolvedEvents()(Backward).head.position
      newSubscription(Some(position), resolveLinkTos = false)
      fishForLiveProcessingStarted()
      val (linked, link) = linkedAndLink()
      expectMsgType[ResolvedEvent].eventRecord mustEqual linked
      expectMsgType[ResolvedEvent]
      val resolvedEvent = expectMsgType[ResolvedEvent]
      resolvedEvent.eventRecord mustEqual link
      resolvedEvent.link must beEmpty
    }

    "catch link events if resolveLinkTos = true" in new SubscribeToAllCatchingUpScope {
      val position = allStreamsResolvedEvents()(Backward).head.position
      newSubscription(Some(position), resolveLinkTos = true)
      fishForLiveProcessingStarted()
      val (linked, link) = linkedAndLink()
      expectMsgType[ResolvedEvent].eventRecord mustEqual linked
      expectMsgType[ResolvedEvent]
      val resolvedEvent = expectMsgType[ResolvedEvent]
      resolvedEvent.eventRecord mustEqual linked
      resolvedEvent.link must beSome(link)
    }
  }

  trait SubscribeToAllCatchingUpScope extends TestConnectionScope {

    def newSubscription(fromPositionExclusive: Option[Position.Exact] = None,
                        resolveLinkTos: Boolean = false,
                        client: ActorRef = testActor) = TestActorRef(new CatchUpSubscriptionActor(
      connection = actor,
      client = client,
      fromPositionExclusive = fromPositionExclusive,
      resolveLinkTos = resolveLinkTos,
      readBatchSize = 500))

    def expectEvents(events: Seq[Event],
                             position: Position = Position.First,
                             testKit: TestKitBase = this): Seq[ResolvedEvent] = {

      def loop(events: List[Event], position: Position): List[ResolvedEvent] = events match {
        case Nil => Nil
        case head :: tail =>
          val resolvedEvent = testKit.expectMsgType[ResolvedEvent]
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

    @tailrec
    final def fishForLiveProcessingStarted(position: Position = Position.First,
                                           testKit: TestKitBase = this): Position = testKit.expectMsgType[AnyRef] match {
      case LiveProcessingStarted => position
      case x: ResolvedEvent =>
        x.position must beGreaterThanOrEqualTo(position)
        fishForLiveProcessingStarted(x.position, testKit)
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