package eventstore

import akka.actor.Status.Failure
import com.typesafe.config.ConfigFactory
import tcp.ConnectionActor

import scala.concurrent.duration._

class OperationTimedOutITest extends util.ActorSpec {
  val actor = system.actorOf(ConnectionActor.props(Settings.Default.copy(operationTimeout = 1.millis)))

  "OperationTimedOut" should {
    /*"be thrown on write events" in new TestScope {
      actor ! WriteEvents(streamId, List(EventData("test")))
      expectOperationTimeout
    }*/

    /*"be thrown on delete stream" in new TestScope {
      actor ! DeleteStream(streamId)
      expectOperationTimeout
    }*/

    /*"be thrown on transaction start" in new TestScope {
      actor ! TransactionStart(streamId)
      expectOperationTimeout
    }*/

    /*"be thrown on transaction write" in new TestScope {
      actor ! TransactionWrite(0, List(EventData("test")))
      expectOperationTimeout
    }*/

    /*"be thrown on transaction commit" in new TestScope {
      actor ! TransactionCommit(0)
      expectOperationTimeout
    }*/

    /*"be thrown on read event" in new TestScope {
      actor ! ReadEvent(streamId)
      expectOperationTimeout
    }*/

    /*"be thrown on read stream events" in new TestScope {
      actor ! ReadStreamEvents(streamId)
      expectOperationTimeout
    }*/

    "be thrown on read all events" in new TestScope {
      actor ! ReadAllEvents(Position.Last, 1000, ReadDirection.Backward)
      expectOperationTimeout
    }

    /*"be thrown on subscribe to stream" in new TestScope {
      actor ! SubscribeTo(streamId)
      expectOperationTimeout
    }*/

    /*"be thrown on subscribe to all" in new TestScope {
      actor ! SubscribeTo(EventStream.All)
      expectOperationTimeout
    }*/

    /*"be thrown on unsubscribe" in new TestScope {
      actor ! Unsubscribe
      expectOperationTimeout
    }*/
  }

  private trait TestScope extends ActorScope {
    val streamId = EventStream.Id(randomUuid.toString)

    def expectOperationTimeout = {
      expectMsgPF() { case Failure(_: OperationTimeoutException) => true }
    }
  }

  override def config = ConfigFactory.parseString("akka.scheduler.tick-duration = 1ms")
}