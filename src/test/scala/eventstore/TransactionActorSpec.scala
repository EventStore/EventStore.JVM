package eventstore

import akka.actor.Terminated
import akka.testkit.{ TestProbe, TestActorRef }
import scala.concurrent.duration._
import util.ActorSpec
import Transaction._

/**
 * @author Yaroslav Klymko
 */
class TransactionActorSpec extends ActorSpec {
  "TransactionActor" should {

    "write" in new TestScope {
      val events = Seq(EventData(eventType = "test"))
      actor ! Write(events)
      expectMsg(TransactionWrite(transactionId, events))

      actor ! TransactionWriteCompleted(invalid)
      client.expectNoMsg(300.millis)

      actor ! TransactionWriteCompleted(transactionId)
      client.expectMsg(WriteCompleted)
    }

    "commit" in new TestScope {
      actor ! Commit
      expectMsg(TransactionCommit(transactionId))

      actor ! TransactionCommitCompleted(invalid)
      client.expectNoMsg(300.millis)

      actor ! TransactionCommitCompleted(transactionId)
      client.expectMsg(CommitCompleted)

      val probe = TestProbe()
      probe watch actor
      probe.expectMsgPF() {
        case Terminated(`actor`) =>
      }
    }
  }

  trait TestScope extends ActorScope {
    val transactionId = 0
    val invalid = 1
    val client = TestProbe()
    val actor = TestActorRef(TransactionActor.props(testActor, client.ref, transactionId))
  }
}