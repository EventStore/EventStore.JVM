package eventstore

import akka.actor.ActorRef
import akka.testkit.{ TestKitBase, TestProbe, TestActorRef }
import akka.actor.Status.Failure
import scala.concurrent.duration._

class SubscribeToStreamCatchingUpITest extends TestConnection {
  sequential

  "subscribe catching up" should {

    "be able to subscribe to non existing stream" in new SubscribeCatchingUpScope {
      newSubscription()
    }

    "be able to subscribe to non existing stream and then catch event" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription()
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectEvent(event)
    }

    "be able to subscribe to non existing stream from number" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      append(newEventData)
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectEvent(event)
    }

    "fail if stream deleted" in new SubscribeCatchingUpScope {
      appendEventToCreateStream()
      deleteStream()
      val subscriptionActor = newSubscription()
      expectMsg(Failure(EsException(EsError.StreamDeleted)))
      expectTerminated(subscriptionActor)
    }

    "allow multiple subscriptions to same stream" in new SubscribeCatchingUpScope {
      val probes = List.fill(5)(TestProbe.apply)
      probes.foreach(x => newSubscription(client = x.ref))
      probes.foreach(_.expectMsg(LiveProcessingStarted))
      val event = append(newEventData)
      probes.foreach(x => expectEvent(event, x))
    }

    "stop subscription after actor stopped" in new SubscribeCatchingUpScope {
      appendEventToCreateStream()
      val subscriptionActor = newSubscription()
      subscriptionActor.stop()
      expectTerminated(subscriptionActor)
    }

    "read all existing events and keep listening to new ones" in new SubscribeCatchingUpScope {
      val event = append(newEventData)
      val subscriptionActor = newSubscription()
      expectEvent(event)

      expectMsg(LiveProcessingStarted)

      expectNoEvents()
      val event2 = append(newEventData)
      expectEvent(event2)
    }

    "filter events and keep listening to new ones" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectMsg(LiveProcessingStarted)
      append(newEventData)
      val event = append(newEventData)
      expectEvent(event)
      expectNoEvents()
      val event2 = append(newEventData)
      expectEvent(event2)
    }

    "filter events and keep listening to new ones" in new SubscribeCatchingUpScope {
      append(newEventData)
      val event = append(newEventData)
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectEvent(event)
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event2 = append(newEventData)
      expectEvent(event2)
    }

    "filter events and work if nothing was written after subscription" in new SubscribeCatchingUpScope {
      append(newEventData)
      val event = append(newEventData)
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectEvent(event)
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
    }

    "read linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = false)
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(link)
      expectMsg(LiveProcessingStarted)
    }

    "read linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = true)
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(ResolvedEvent(linked, link))
      expectMsg(LiveProcessingStarted)
    }

    "catch linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = false)
      expectMsg(LiveProcessingStarted)
      val (linked, link) = linkedAndLink()
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(link)
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = true)
      expectMsg(LiveProcessingStarted)
      val (linked, link) = linkedAndLink()
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(ResolvedEvent(linked, link))
    }
  }

  trait SubscribeCatchingUpScope extends TestConnectionScope {
    def expectNoEvents() = expectNoMsg(1.second)

    def newSubscription(
      fromNumberExclusive: Option[EventNumber.Exact] = None,
      resolveLinkTos: Boolean = false,
      client: ActorRef = testActor) = {

      val a = TestActorRef(StreamSubscriptionActor.props(
        connection = actor,
        client = client,
        streamId = streamId,
        fromNumberExclusive = fromNumberExclusive,
        resolveLinkTos = resolveLinkTos,
        readBatchSize = 500))
      watch(a)
      a
    }

    def expectActorTerminated(actor: TestActorRef[_]) {
      expectTerminated(actor)
      actor.underlying.isTerminated must beTrue
      expectNoEvents()
    }

    def expectEvent(x: Event, probe: TestKitBase = this) = probe.expectMsg(x)
  }
}

