package eventstore
package akka

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.annotation.tailrec
import _root_.akka.testkit.{TestActorRef, TestKitBase, TestProbe}
import _root_.akka.actor.ActorRef
import ReadDirection.Backward

class SubscribeToAllCatchingUpITest extends TestConnection {
  sequential

  "subscribe to all catching up" should {
    "be able to subscribe to empty db" in {
      // todo how to setup empty db ?
      todo
    }

    "read all existing events and keep listening to new ones" in new SubscribeToAllCatchingUpScope {
      appendMany()

      val existing = readAllEventsCompleted(Position.First, 1000)(Backward)
        .events.map(_.event.data).reverse
      val subscriptionActor = newSubscription()
      expectEvents(existing)

      fishForLiveProcessingStarted()

      val newEvents = writeAsync()
      expectEvents(newEvents)
    }

    "filter events and keep listening to new ones" in new SubscribeToAllCatchingUpScope {
      val many = 10
      appendMany(many)
      val position = allStreamsEvents()(Backward).take(many).last.position
      val subscriptionActor = newSubscription(Some(position))

      val processingStartedPosition = fishForLiveProcessingStarted(position)

      val events = writeAsync()
      expectEvents(events, processingStartedPosition)
    }

    "filter events and work if nothing was written after subscription" in new SubscribeToAllCatchingUpScope {
      val many = 10
      appendMany(many)
      val position = allStreamsEvents()(Backward).take(many).last.position
      val subscriptionActor = newSubscription(Some(position))

      fishForLiveProcessingStarted(position)
      expectNoEvents()
    }

    "allow multiple subscriptions" in new SubscribeToAllCatchingUpScope {
      val position = allStreamsEvents()(Backward).take(10).last.position
      val probes = List.fill(5)(TestProbe.apply())
      probes.foreach(x => newSubscription(Some(position), client = x.ref))
      probes.foreach(x => fishForLiveProcessingStarted(testKit = x))
      val event = append(newEventData).data
      probes.foreach(x => expectEvents(List(event), testKit = x))
    }

    "read link events if resolveLinkTos = false" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition
      val (linked, link) = linkedAndLink()
      newSubscription(Some(last))
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent()
      expectPlainIndexedEvent().event mustEqual link
      fishForLiveProcessingStarted(last)
    }

    "read link events if resolveLinkTos = true" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition
      val (linked, link) = linkedAndLink()
      newSubscription(Some(last), resolveLinkTos = true)
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent()
      expectPlainIndexedEvent().event mustEqual ResolvedEvent(linked, link)
      fishForLiveProcessingStarted(last)
    }

    "catch link events if resolveLinkTos = false" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition
      newSubscription(Some(last))
      fishForLiveProcessingStarted(last)
      val (linked, link) = linkedAndLink()
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent()
      expectPlainIndexedEvent().event mustEqual link
    }

    "catch link events if resolveLinkTos = true" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition
      newSubscription(Some(last), resolveLinkTos = true)
      fishForLiveProcessingStarted(last)
      val (linked, link) = linkedAndLink()
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent()
      expectPlainIndexedEvent().event mustEqual ResolvedEvent(linked, link)
    }

    "read link events if resolveLinkTos = false and deleted linked" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition
      val linkedData = newEventData.copy(eventType = "linked")
      val linkedStreamId = newStreamId
      val linked = EventRecord(
        linkedStreamId,
        writeEventsCompleted(List(linkedData), streamId = linkedStreamId).get.start,
        linkedData,
        Some(date)
      )
      val link = append(linked.link(randomUuid))

      actor ! DeleteStream(linkedStreamId, hard = true)
      expectMsgType[DeleteStreamCompleted]

      newSubscription(Some(last))
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent().event mustEqual link
      fishForLiveProcessingStarted(last)
    }

    "read link events if resolveLinkTos = true and deleted linked" in new SubscribeToAllCatchingUpScope {
      val last = lastPosition

      val linkedData = newEventData.copy(eventType = "linked")
      val linkedStreamId = newStreamId
      val linked = EventRecord(
        linkedStreamId,
        writeEventsCompleted(List(linkedData), streamId = linkedStreamId).get.start,
        linkedData,
        Some(date)
      )
      val link = append(linked.link(randomUuid))

      actor ! DeleteStream(linkedStreamId, hard = true)
      expectMsgType[DeleteStreamCompleted]

      newSubscription(Some(last), resolveLinkTos = true)
      expectPlainIndexedEvent().event mustEqual linked
      expectPlainIndexedEvent().event mustEqual ResolvedEvent(EventRecord.Deleted, link).fixDate
      fishForLiveProcessingStarted(last)
    }

  }

  private trait SubscribeToAllCatchingUpScope extends TestConnectionScope {

    def lastPosition = allStreamsEvents()(Backward).head.position

    def newSubscription(
      fromPositionExclusive: Option[Position.Exact] = None,
      resolveLinkTos:        Boolean                = false,
      client:                ActorRef               = testActor
    ) = TestActorRef(SubscriptionActor.props(
      connection = actor,
      client = client,
      fromPositionExclusive = fromPositionExclusive,
      credentials = None,
      settings = Settings.Default.copy(resolveLinkTos = resolveLinkTos)
    ))

    def expectEvents(
      events:   List[EventData],
      position: Position        = Position.First,
      testKit:  TestKitBase     = this
    ): List[IndexedEvent] = {

      def loop(events: List[EventData], position: Position): List[IndexedEvent] = events match {
        case Nil => Nil
        case head :: tail =>
          val indexedEvent = testKit.expectIndexedEvent
          indexedEvent.position must beGreaterThanOrEqualTo(position)
          indexedEvent.event must beAnInstanceOf[EventRecord]
          if (indexedEvent.event.data == head) indexedEvent :: loop(tail, indexedEvent.position)
          else loop(events, indexedEvent.position)
      }
      loop(events, position)
    }

    @tailrec final def expectNoEvents(): Unit = {
      receiveOne(1.second) match {
        case null =>
        case msg =>
          msg must beAnInstanceOf[IndexedEvent]
          val indexedEvent = msg.asInstanceOf[IndexedEvent]
          val streamId = indexedEvent.event.streamId
          streamId.isSystem must beTrue
          expectNoEvents()
      }
    }

    @tailrec
    final def fishForLiveProcessingStarted(
      position: Position    = Position.First,
      testKit:  TestKitBase = this
    ): Position = testKit.expectMsgType[AnyRef] match {
      case LiveProcessingStarted => position
      case IndexedEvent(_, x) =>
        x must beGreaterThanOrEqualTo(position)
        fishForLiveProcessingStarted(x, testKit)
    }

    def writeAsync(size: Int = 20): List[EventData] = {
      val events = (1 to size).map(_ => newEventData).toList
      import system.dispatcher
      Future {
        writeEventsCompleted(events, testKit = TestProbe())
      }
      events
    }

    def expectPlainIndexedEvent(max: Duration = Duration.Undefined): IndexedEvent =
      new RichTestKitBase(this).expectPlainIndexedEvent(max)

    def expectIndexedEvent: IndexedEvent = new RichTestKitBase(this).expectIndexedEvent

    implicit class RichTestKitBase(self: TestKitBase) {

      def expectPlainIndexedEvent(max: Duration = Duration.Undefined): IndexedEvent = {
        self.fishForSpecificMessage[IndexedEvent](max) {
          case e: IndexedEvent if e.event.isPlainEvent => e.fixDate
        }
      }

      def expectIndexedEvent: IndexedEvent = self.expectMsgType[IndexedEvent].fixDate
    }
  }
}