package eventstore

import akka.actor.{ Props, SupervisorStrategy, Actor, ActorRef }
import akka.testkit.{ TestProbe, TestActorRef }
import scala.concurrent.duration._
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class SubscribeCatchingUpITest extends TestConnection {

  "subscribe catching up" should {

    "be able to subscribe to non existing stream" in new SubscribeCatchingUpScope {
      newSubscription()
    }

    "be able to subscribe to non existing stream and then catch event" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription()
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectMsgType[Event] mustEqual event
    }

    "be able to subscribe to non existing stream from number" in new SubscribeCatchingUpScope {
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      append(newEventData)
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
      val event = append(newEventData)
      expectMsgType[Event] mustEqual event
    }

    "fail if stream deleted" in new SubscribeCatchingUpScope {
      appendEventToCreateStream()
      deleteStream()
      val subscriptionActor = newSubscription()
      expectTerminated(subscriptionActor)
    }

    "allow multiple subscriptions to same stream" in new SubscribeCatchingUpScope {
      val probes = List.fill(5)(TestProbe.apply)
      probes.foreach(x => newSubscription(client = x.ref))
      probes.foreach(_.expectMsg(LiveProcessingStarted))
      val event = append(newEventData)
      probes.foreach(_.expectMsgType[Event] mustEqual event)
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

    "filter events and work if nothing was written after subscription" in new SubscribeCatchingUpScope {
      append(newEventData)
      val event = append(newEventData)
      val subscriptionActor = newSubscription(Some(EventNumber(0)))
      expectMsgType[Event] mustEqual event
      expectMsg(LiveProcessingStarted)
      expectNoEvents()
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

    def newSubscription(
      fromNumberExclusive: Option[EventNumber.Exact] = None,
      resolveLinkTos: Boolean = false,
      client: ActorRef = testActor) = {

      class Supervisor extends Actor {
        override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
        def receive = PartialFunction.empty
      }

      val a = TestActorRef(Props(new StreamCatchUpSubscriptionActor(
        connection = actor,
        client = client,
        streamId = streamId,
        fromNumberExclusive = fromNumberExclusive,
        resolveLinkTos = resolveLinkTos,
        readBatchSize = 500)), TestActorRef(new Supervisor), "")
      watch(a)
      a
    }

    def expectActorTerminated(actor: TestActorRef[_]) {
      expectTerminated(actor)
      actor.underlying.isTerminated must beTrue
      expectNoEvents()
    }
  }
}

