package eventstore

import akka.actor.ActorRef
import akka.testkit.{ TestProbe, TestActorRef }
import scala.concurrent.duration._
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class SubscribeCatchingUpITest extends TestConnection {

  "subscribe catching up" should {

    "be able to subscribe to non existing stream" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription()
      subscriptionActor.stop()
      subscriptionActor.underlying.isTerminated must beTrue
      expectNoEvents()
    }

    "be able to subscribe to non existing stream and then catch event" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription()
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectMsgType[Event] mustEqual event
      subscriptionActor.stop()
      subscriptionActor.underlying.isTerminated must beTrue
      expectNoEvents()
    }

    "be able to subscribe to non existing stream from number" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      append(newEventData)
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectMsgType[Event] mustEqual event
      subscriptionActor.stop()
      subscriptionActor.underlying.isTerminated must beTrue
      expectNoEvents()
    }

    "fail if stream deleted" in new SubscribeCatchingUpScope {
      appendEventToCreateStream()
      deleteStream()
      newSubscription()
      expectMsg("error")
    }

    "allow multiple subscriptions to same stream" in new SubscribeCatchingUpScope {
      val probes = List.fill(5)(TestProbe.apply)
      probes.foreach(x => newSubscription(client = x.ref))
      probes.foreach(_.expectMsg(LiveProcessingStarted))
      val event = append(newEventData)
      probes.foreach(_.expectMsgType[Event] mustEqual event)
    }

    "call dropped callback after stop method call" in {
      /*        public void call dropped callback after stop method call()
        {
            const string stream = "call dropped callback after stop method call";
            using (var store = TestConnection.Create( node.TcpEndPoint))
            {
                store.Connect();

                var dropped = new CountdownEvent(1);
                var subscription = store.SubscribeToStreamFrom(stream,
                                                               null,
                                                               false,
                                                               (x, y) => { },
                                                                 => Log.Info("Live processing started."),
                                                               (x, y, z) => dropped.Signal());
                Assert.IsFalse(dropped.Wait(0));
                subscription.Stop(Timeout);
                Assert.IsTrue(dropped.Wait(Timeout));
            }
        }
*/
      todo
    }

    "read all existing events and keep listening to new ones" in new SubscribeCatchingUpScope {
      val event = append(newEventData)
      val subscriptionActor = newSubscription()
      expectMsgType[Event] mustEqual event

      expectMsg(LiveProcessingStarted)

      expectNoEvents()
      val event2 = append(newEventData)
      expectMsgType[Event] mustEqual event2
    }

    "filter events and keep listening to new ones" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectMsg(LiveProcessingStarted)
      append(newEventData)
      val event = append(newEventData)
      expectMsgType[Event] mustEqual event
      expectNoEvents()
      val event2 = append(newEventData)
      expectMsgType[Event] mustEqual event2
    }

    "filter events and keep listening to new ones" in new SubscribeCatchingUpScope {
      append(newEventData)
      val event = append(newEventData)
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectMsgType[Event] mustEqual event
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event2 = append(newEventData)
      expectMsgType[Event] mustEqual event2
    }

    "filter events and work if nothing was written after subscription" in {
      /*public void filter events and work if nothing was written after subscription()
        {
            const string stream = "filter events and work if nothing was written after subscription";
            using (var store = TestConnection.Create( node.TcpEndPoint))
            {
                store.Connect();

                var events = new List<ResolvedEvent>();
                var appeared = new CountdownEvent(10);
                var dropped = new CountdownEvent(1);

                for (int i = 0; i < 20; ++i)
                {
                    store.AppendToStream(stream, i-1, new EventData(Guid.NewGuid(), "et-" + i.ToString(), false, new byte[3], null));
                }

                var subscription = store.SubscribeToStreamFrom(stream,
                                                               9,
                                                               false,
                                                               (x, y) =>
                                                               {
                                                                   events.Add(y);
                                                                   appeared.Signal();
                                                               },
                                                                 => Log.Info("Live processing started."),
                                                               (x, y, z) => dropped.Signal());
                if (!appeared.Wait(Timeout))
                {
                    Assert.IsFalse(dropped.Wait(0), "Subscription was dropped prematurely.");
                    Assert.Fail("Couldn't wait for all events.");
                }

                Assert.AreEqual(10, events.Count);
                for (int i = 0; i < 10; ++i)
                {
                    Assert.AreEqual("et-" + (i + 10).ToString(), events[i].OriginalEvent.EventType);
                }

                Assert.IsFalse(dropped.Wait(0));
                subscription.Stop(Timeout);
                Assert.IsTrue(dropped.Wait(Timeout));

                Assert.AreEqual(events.Last().OriginalEventNumber, subscription.LastProcessedEventNumber);
            }
        }*/
      todo
    }

    "read linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = false)
      expectMsgType[Event] mustEqual linked
      expectMsgType[Event]
      expectMsgType[Event] mustEqual link
      expectMsg(LiveProcessingStarted)
    }

    "read linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = true)
      expectMsgType[Event] mustEqual linked
      expectMsgType[Event]
      expectMsgType[Event] mustEqual ResolvedEvent(linked, link)
      expectMsg(LiveProcessingStarted)
    }

    "catch linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = false)
      expectMsg(LiveProcessingStarted)
      val (linked, link) = linkedAndLink()
      expectMsgType[Event] mustEqual linked
      expectMsgType[Event]
      expectMsgType[Event] mustEqual link
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = true)
      expectMsg(LiveProcessingStarted)
      val (linked, link) = linkedAndLink()
      expectMsgType[Event] mustEqual linked
      expectMsgType[Event]
      expectMsgType[Event] mustEqual ResolvedEvent(linked, link)
    }
  }

  trait SubscribeCatchingUpScope extends TestConnectionScope {
    def expectNoEvents() = expectNoMsg(FiniteDuration(1, SECONDS))

    def newSubscription(fromNumberExclusive: Option[EventNumber.Exact] = None,
      resolveLinkTos: Boolean = false,
      client: ActorRef = testActor) = TestActorRef(new StreamCatchUpSubscriptionActor(
      connection = actor,
      client = client,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      resolveLinkTos = resolveLinkTos,
      readBatchSize = 500))
  }
}

