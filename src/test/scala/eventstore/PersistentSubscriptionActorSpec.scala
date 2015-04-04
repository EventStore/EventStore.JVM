package eventstore
import akka.actor.ActorRef
import akka.testkit.TestActorRef
import eventstore.PersistentSubscription.{ Ack, Connect, Connected, EventAppeared }
import eventstore.ReadDirection.Forward

class PersistentSubscriptionActorSpec extends AbstractSubscriptionActorSpec {
  "PersistentSubscriptionActor" should {
    "should connect to the eventstore" in new PersistentSubscriptionActorScope {
      override def eventNumber = Some(EventNumber.Last)
      connection.expectMsgType[Connect]
    }

    "should send an ack after message was received" in new PersistentSubscriptionActorScope {
      override def eventNumber = Some(EventNumber.Current)
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! EventAppeared(new EventRecord(streamId, EventNumber.Exact(0), new EventData("test")))
      connection.expectMsgType[Ack]
    }

    "should read from the first event if no event number is passed" in new PersistentSubscriptionActorScope {
      connection expectMsg readEvents(0)
    }

    "should read from the event number that was passed in" in new PersistentSubscriptionActorScope {
      connection expectMsg readEvents(100)

      override def eventNumber: Option[EventNumber] = Some(EventNumber(100))
    }

    "should subscribe if last even was passed in" in new PersistentSubscriptionActorScope {
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      connection.expectNoMsg()

      override def eventNumber: Option[EventNumber] = Some(EventNumber.Last)
    }

    "should trigger LiveProcessingStarted and Ack" in new PersistentSubscriptionActorScope {
      val event: Event = newEvent(1)
      connection.expectMsgType[Connect]
      actor ! connected(Some(EventNumber.Exact(0)))
      actor ! eventAppeared(event)

      expectMsg(LiveProcessingStarted)
      expectMsg(event)
      expectAck()

      override def eventNumber: Option[EventNumber] = Some(EventNumber.Last)
    }
  }

  trait PersistentSubscriptionActorScope extends AbstractScope {
    lazy val streamId = EventStream.Id(PersistentSubscriptionActor.getClass.getSimpleName + "-" + randomUuid.toString)
    def eventNumber: Option[EventNumber] = None
    def groupName: String = randomUuid.toString

    def settings: Settings = Settings.Default.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos)

    override def createActor(): ActorRef = {
      val props = PersistentSubscriptionActor.props(
        connection = connection.ref,
        client = testActor,
        streamId = streamId,
        groupName = groupName,
        fromNumberExclusive = eventNumber,
        settings = settings,
        credentials = None
      )
      TestActorRef(props)
    }

    def expectAck(): Unit = connection.expectMsgType[Ack]

    def readEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber.Exact(number), mock[EventData])

    def eventAppeared(event: Event) =
      EventAppeared(event)

    def connected(eventNumber: Option[EventNumber.Exact] = None): Connected =
      Connected(randomUuid.toString, 5000, eventNumber)
  }
}
