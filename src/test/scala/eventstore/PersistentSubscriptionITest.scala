package eventstore

import eventstore.PersistentSubscription._
import scala.concurrent.duration._

class PersistentSubscriptionITest extends TestConnection {
  "PersistentSubscription" should {
    "create persistent subscription" in new PsScope {
      actor ! Create(streamId, groupName)
      expectMsg(CreateCompleted)
    }

    "fail to create with unknown strategy" in new PsScope {
      val settings = PersistentSubscriptionSettings(consumerStrategy = ConsumerStrategy.Custom("test"))
      actor ! Create(streamId, groupName, settings)
      expectEsException() must throwA[ServerErrorException]
    }

    "fail to create if already created" in new PsScope {
      actor ! Create(streamId, groupName)
      expectMsg(CreateCompleted)
      actor ! Create(streamId, groupName)
      expectEsException() must throwA[InvalidOperationException]
    }

    "update persistent subscription" in new PsScope {
      actor ! Create(streamId, groupName)
      expectMsg(CreateCompleted)

      val settings = PersistentSubscriptionSettings(consumerStrategy = ConsumerStrategy.DispatchToSingle)
      actor ! Update(streamId, groupName, settings)
      expectMsg(UpdateCompleted)
    }

    "delete persistent subscription" in new PsScope {
      actor ! Create(streamId, groupName)
      expectMsg(CreateCompleted)

      actor ! Delete(streamId, groupName)
      expectMsg(DeleteCompleted)
    }

    "connect to stream" in new PsScope {
      appendEventToCreateStream()
      val settings = PersistentSubscriptionSettings(startFrom = EventNumber.First)
      actor ! Create(streamId, groupName, settings)
      expectMsg(CreateCompleted)

      actor ! Connect(streamId, groupName)
      val connected = expectMsgType[Connected]
      connected.subscriptionId shouldEqual s"${streamId.streamId}::$groupName"
      connected.lastEventNumber should beSome(EventNumber.First)
      expectNoMsg(1.seconds)
    }

    "connect to system stream" in new PsScope {
      val stream = EventStream.System.`$persistentSubscriptionConfig`
      actor ! Create(stream, groupName)
      expectMsg(CreateCompleted)

      actor ! Connect(stream, groupName)
      val connected = expectMsgType[Connected]
      connected.subscriptionId shouldEqual s"${stream.streamId}::$groupName"
      expectNoMsg(1.seconds)
    }

    "connect to meta stream" in new PsScope {
      val stream = EventStream.Metadata("persistentSubscriptionConfig")
      actor ! Create(stream, groupName)
      expectMsg(CreateCompleted)

      actor ! Connect(stream, groupName)
      val connected = expectMsgType[Connected]
      connected.subscriptionId shouldEqual s"${stream.streamId}::$groupName"
      expectNoMsg(1.seconds)
    }
  }

  private trait PsScope extends TestConnectionScope {
    val groupName = randomUuid.toString
  }
}