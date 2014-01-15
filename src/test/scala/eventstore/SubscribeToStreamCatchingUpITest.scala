package eventstore

import akka.actor.{ SupervisorStrategy, Actor, ActorRef }
import akka.testkit.{ TestKitBase, TestProbe, TestActorRef }
import akka.actor.Status.Failure
import scala.concurrent.duration._

class SubscribeToStreamCatchingUpITest extends TestConnection {

  "subscribe catching up" should {

    "be able to subscribe to non existing stream" in new SubscribeCatchingUpScope {
      newSubscription()
    }

    "be able to subscribe to non existing stream and then catch event" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription()
      expectMsg(Subscription.LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectEvent(event)
    }

    "be able to subscribe to non existing stream from number" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      append(newEventData)
      expectMsg(Subscription.LiveProcessingStarted)
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
      probes.foreach(_.expectMsg(Subscription.LiveProcessingStarted))
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

      expectMsg(Subscription.LiveProcessingStarted)

      expectNoEvents()
      val event2 = append(newEventData)
      expectEvent(event2)
    }

    "filter events and keep listening to new ones" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectMsg(Subscription.LiveProcessingStarted)
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
      expectMsg(Subscription.LiveProcessingStarted)
      expectNoEvents()
      val event2 = append(newEventData)
      expectEvent(event2)
    }

    "filter events and work if nothing was written after subscription" in new SubscribeCatchingUpScope {
      append(newEventData)
      val event = append(newEventData)
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectEvent(event)
      expectMsg(Subscription.LiveProcessingStarted)
      expectNoEvents()
    }

    "read linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = false)
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(link)
      expectMsg(Subscription.LiveProcessingStarted)
    }

    "read linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      val (linked, link) = linkedAndLink()
      newSubscription(resolveLinkTos = true)
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(ResolvedEvent(linked, link))
      expectMsg(Subscription.LiveProcessingStarted)
    }

    "catch linked events if resolveLinkTos = false" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = false)
      expectMsg(Subscription.LiveProcessingStarted)
      val (linked, link) = linkedAndLink()
      expectEvent(linked)
      expectMsgType[Event]
      expectEvent(link)
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeCatchingUpScope {
      newSubscription(resolveLinkTos = true)
      expectMsg(Subscription.LiveProcessingStarted)
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

      class Supervisor extends Actor {
        override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
        def receive = PartialFunction.empty
      }

      val a = TestActorRef(StreamSubscriptionActor.props(
        connection = actor,
        client = client,
        streamId = streamId,
        fromNumberExclusive = fromNumberExclusive,
        resolveLinkTos = resolveLinkTos,
        readBatchSize = 500), TestActorRef(new Supervisor), "")
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

