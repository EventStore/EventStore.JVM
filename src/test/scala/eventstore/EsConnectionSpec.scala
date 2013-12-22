package eventstore

import org.specs2.mock.Mockito
import scala.util.Success
import util.ActorSpec

/**
 * @author Yaroslav Klymko
 */
class EsConnectionSpec extends ActorSpec with Mockito {
  "EventStoreConnection.future" should {
    "write events" in new TestScope {
      verifyOutIn(mock[WriteEvents], mock[WriteEventsCompleted])
    }

    "delete stream" in new TestScope {
      verifyOutIn(mock[DeleteStream], DeleteStreamCompleted)
    }

    "transaction start" in new TestScope {
      verifyOutIn(mock[TransactionStart], mock[TransactionStartCompleted])
    }

    "transaction write" in new TestScope {
      verifyOutIn(mock[TransactionWrite], mock[TransactionWriteCompleted])
    }

    "transaction commit" in new TestScope {
      verifyOutIn(mock[TransactionCommit], mock[TransactionCommitCompleted])
    }

    "read event" in new TestScope {
      verifyOutIn(mock[ReadEvent], mock[ReadEventCompleted])
    }

    "read stream events" in new TestScope {
      verifyOutIn(mock[ReadStreamEvents], mock[ReadStreamEventsCompleted])
    }

    "read all events" in new TestScope {
      verifyOutIn(mock[ReadAllEvents], mock[ReadAllEventsCompleted])
    }

    "subscribe to" in new TestScope {
      verifyOutIn(mock[SubscribeTo], mock[SubscribeToStreamCompleted])
    }
  }

  trait TestScope extends ActorScope {
    val streamId = EventStream("EventStream")
    val events = Seq(EventData(eventType = "eventType"))
    val connection = new EsConnection(testActor, Settings(defaultCredentials = None))

    def verifyOutIn[OUT <: Out, IN <: In](out: OUT, in: In)(implicit outIn: OutInTag[OUT, IN]) {
      val future = connection.future(out)(outIn = outIn)
      expectMsg(out)
      future.value must beNone
      lastSender ! in
      future.value must beSome(Success(in))
    }
  }
}