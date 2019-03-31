package eventstore
package akka

import scala.concurrent.duration._
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.Terminated
import _root_.akka.testkit.{TestActorRef, TestProbe}
import TransactionActor._

class TransactionActorSpec extends ActorSpec {

  "TransactionActor" should {

    "start" in new StartTransactionScope {
      actor ! GetTransactionId
      actor ! Write(es1)
      actor ! Write(es2)

      connection.expectMsg(kickOff.data)
      expectNoMsgs()

      startCompleted()

      expectMsg(TransactionId(transactionId))

      expectWrite(es1)

      expectNoMsgs()

      actor ! Write(es3)
      writeCompleted()

      expectWrite(es2)
      writeCompleted()

      expectMsg(WriteCompleted)
      expectMsg(WriteCompleted)

      expectWrite(es3)
      writeCompleted()
      expectMsg(WriteCompleted)

      verifyTransactionId()
    }

    "write" in new ContinueTransactionScope {
      actor ! Write(es1)
      actor ! Write(es2)
      expectWrite(es1)

      writeCompleted(invalid)
      expectNoMsgs()

      writeCompleted()
      expectMsg(WriteCompleted)

      expectWrite(es2)

      verifyTransactionId()

      writeCompleted()
      expectMsg(WriteCompleted)
    }

    "commit" in new ContinueTransactionScope {
      actor ! Commit
      expectCommit

      expectNoMsgs()
      verifyTransactionId()

      writeCompleted(invalid)

      expectNoMsgs()

      commitCompleted(None)
      expectMsg(CommitCompleted(None))

      expectTerminated
    }

    "commit from stash" in new ContinueTransactionScope {
      actor ! Write(es1)
      actor ! Write(es2)
      actor ! Commit
      actor ! Write(es3)

      expectWrite(es1)
      expectNoMsgs()
      verifyTransactionId()

      writeCompleted()
      expectMsg(WriteCompleted)

      expectWrite(es2)
      expectNoMsgs()
      verifyTransactionId()

      writeCompleted()
      expectMsg(WriteCompleted)

      expectCommit
      expectNoMsgs()
      verifyTransactionId()

      commitCompleted(Some(EventNumber.First to EventNumber.Exact(2)))
      expectMsg(CommitCompleted(Some(EventNumber.First to EventNumber.Exact(2))))

      expectTerminated
    }

    "start and commit" in new StartTransactionScope {
      actor ! Commit
      actor ! Write(es1)
      actor ! Write(es2)

      connection.expectMsg(kickOff.data)
      startCompleted()
      expectCommit
      commitCompleted(Some(EventNumber.Exact(0) to EventNumber.Exact(2)))
      expectMsg(CommitCompleted(Some(EventNumber.Exact(0) to EventNumber.Exact(2))))
      expectTerminated
      expectNoMsgs()
    }

    "handle failures while starting" in new StartTransactionScope {
      sendFailure
    }

    "handle failures while starting and reply with it on GetTransactionId" in new StartTransactionScope {
      actor ! GetTransactionId
      sendFailure
      expectFailure
    }

    "handle failures while writing" in new ContinueTransactionScope {
      actor ! Write(es1)
      expectWrite(es1)
      sendFailure
      expectFailure
    }

    "handle failures while committing" in new ContinueTransactionScope {
      actor ! Commit
      expectCommit
      sendFailure
      expectFailure
    }
  }

  trait TestScope extends ActorScope {
    val es1 = events("1")
    val es2 = events("2")
    val es3 = events("3")

    val transactionId = 0L
    val invalid = 1L
    val connection = TestProbe()
    val actor = watch(TestActorRef(TransactionActor.props(connection.ref, kickOff)))

    def expectNoMsgs(): Unit = {
      val duration = 200.millis
      expectNoMessage(duration)
      connection.expectNoMessage(duration)
    }

    def startCompleted() = actor ! TransactionStartCompleted(transactionId)

    def expectWrite(xs: List[EventData]) = connection.expectMsg(TransactionWrite(transactionId, xs))
    def writeCompleted(transactionId: Long = this.transactionId) = actor ! TransactionWriteCompleted(transactionId)

    def expectCommit = connection.expectMsg(TransactionCommit(transactionId))

    def commitCompleted(range: Option[EventNumber.Range]): Unit = {
      actor ! TransactionCommitCompleted(transactionId, range, None)
    }

    def expectTerminated(): Unit = expectMsgPF() {
      case Terminated(`actor`) =>
    }

    val failure = Failure(new AccessDeniedException("test"))

    def sendFailure() = {
      (actor ! failure) mustNotEqual throwAn[EsException]
    }

    def expectFailure = expectMsg(failure)

    def verifyTransactionId(): Unit = {
      actor ! GetTransactionId
      expectMsg(TransactionId(transactionId))
    }

    def events(label: String) = List(EventData(label, randomUuid))

    def kickOff: Kickoff
  }

  trait StartTransactionScope extends TestScope {
    def kickOff = Start(TransactionStart(EventStream.Id("stream")))
  }

  trait ContinueTransactionScope extends TestScope {
    def kickOff = Continue(transactionId)
  }
}