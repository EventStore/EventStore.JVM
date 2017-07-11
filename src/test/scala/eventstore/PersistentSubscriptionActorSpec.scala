package eventstore
import akka.actor.ActorRef
import akka.testkit.TestActorRef
import eventstore.PersistentSubscription._
import eventstore.ReadDirection.Forward

class PersistentSubscriptionActorSpec extends AbstractSubscriptionActorSpec {
  "PersistentSubscriptionActor" should {
    "should connect to the eventstore" in new PersistentSubscriptionActorScope {
      connection.expectMsgType[Connect]
    }

    "should send an ack after message was received" in new PersistentSubscriptionActorScope {
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! EventAppeared(new EventRecord(streamId, EventNumber.Exact(0), new EventData("test")))
      connection.expectMsgType[Ack]
    }

    "should subscribe if last event was passed in" in new PersistentSubscriptionActorScope {
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      connection.expectNoMsg()
    }

    "should trigger LiveProcessingStarted and Ack" in new PersistentSubscriptionActorScope {
      val event: Event = newEvent(1)
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! eventAppeared(event)

      expectMsg(event)
      expectAck()
      expectMsg(LiveProcessingStarted)
    }

    "should send manual ack" in new ManualAckPersistentSubscriptionActorScope {
      val event: Event = newEvent(1)
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! eventAppeared(event)

      expectMsg(event)
      expectMsg(LiveProcessingStarted)
      expectNoMsg()

      actor ! PersistentSubscriptionActor.ManualAck(event.data.eventId)
      expectAck()
    }

    "should send manual nak" in new ManualNakPersistentSubscriptionActorScope {
      val event: Event = newEvent(1)
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! eventAppeared(event)

      expectMsg(event)
      expectMsg(LiveProcessingStarted)
      expectNoMsg()

      actor ! PersistentSubscriptionActor.ManualNak(event.data.eventId)
      expectNak()
    }
  }

  trait PersistentSubscriptionActorScope extends AbstractScope {
    lazy val streamId = EventStream.Id(PersistentSubscriptionActor.getClass.getSimpleName + "-" + randomUuid.toString)
    def groupName: String = randomUuid.toString

    def settings: Settings = Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos)

    override def createActor(): ActorRef = {
      val props = PersistentSubscriptionActor.props(
        connection = connection.ref,
        client = testActor,
        streamId = streamId,
        groupName = groupName,
        settings = settings,
        credentials = None
      )
      TestActorRef(props)
    }

    def expectAck(): Unit = connection.expectMsgType[Ack]

    def expectNak(): Unit = connection.expectMsgType[Nak]

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber.Exact(number), mock[EventData])

    def eventAppeared(event: Event) =
      EventAppeared(event)

    def expectEvent =
      expectMsgType[EventAppeared]

    def connected(eventNumber: Option[EventNumber.Exact] = None): Connected =
      Connected(randomUuid.toString, 5000, eventNumber)
  }

  trait ManualAckPersistentSubscriptionActorScope extends PersistentSubscriptionActorScope {
    override def createActor(): ActorRef = {
      val props = PersistentSubscriptionActor.props(
        connection = connection.ref,
        client = testActor,
        streamId = streamId,
        groupName = groupName,
        settings = settings,
        credentials = None,
        autoAck = false
      )
      TestActorRef(props)
    }
  }

  trait ManualNakPersistentSubscriptionActorScope extends PersistentSubscriptionActorScope {
    override def createActor(): ActorRef = {
      val props = PersistentSubscriptionActor.props(
        connection = connection.ref,
        client = testActor,
        streamId = streamId,
        groupName = groupName,
        settings = settings,
        credentials = None,
        autoAck = false
      )
      TestActorRef(props)
    }
  }
}
