package eventstore

import akka.actor.Terminated
import akka.testkit.{ TestProbe, TestActorRef }
import scala.concurrent.duration._
import util.ActorSpec
import TransactionActor._
import akka.actor.Status.Failure

/**
 * @author Yaroslav Klymko
 */
class TransactionActorSpec extends ActorSpec {
  "TransactionActor" should {

    "start" in new StartTransactionScope {
      actor ! GetTransactionId
      actor ! Write(es1)
      actor ! Write(es2)

      connection.expectMsg(kickOff.data)
      expectNoMsgs()

      startCompleted

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

      commitCompleted
      expectMsg(CommitCompleted)

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

      commitCompleted
      expectMsg(CommitCompleted)

      expectTerminated
    }

    "start and commit" in new StartTransactionScope {
      actor ! Commit
      actor ! Write(es1)
      actor ! Write(es2)

      connection.expectMsg(kickOff.data)
      startCompleted
      expectCommit
      commitCompleted
      expectMsg(CommitCompleted)
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

    val transactionId = 0
    val invalid = 1
    val connection = TestProbe()
    val actor = watch(TestActorRef(TransactionActor.props(connection.ref, kickOff)))

    def expectNoMsgs() {
      val duration = 200.millis
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    def startCompleted = actor ! TransactionStartCompleted(transactionId)

    def expectWrite(xs: Seq[EventData]) = connection.expectMsg(TransactionWrite(transactionId, xs))
    def writeCompleted(transactionId: Long = this.transactionId) = actor ! TransactionWriteCompleted(transactionId)

    def expectCommit = connection.expectMsg(TransactionCommit(transactionId))
    def commitCompleted = actor ! TransactionCommitCompleted(transactionId)

    def expectTerminated = expectMsgPF() {
      case Terminated(`actor`) =>
    }

    val failure = Failure(EventStoreException(EventStoreError.AccessDenied))

    def sendFailure {
      (actor ! failure) mustNotEqual throwAn[EventStoreException]
    }

    def expectFailure = expectMsg(failure)

    def verifyTransactionId() {
      actor ! GetTransactionId
      expectMsg(TransactionId(transactionId))
    }

    def events(label: String) = Seq(EventData(eventType = label))
    def kickOff: Kickoff
  }

  trait StartTransactionScope extends TestScope {
    def kickOff = Start(TransactionStart(EventStream.Id("stream")))
  }

  trait ContinueTransactionScope extends TestScope {
    def kickOff = Continue(transactionId)
  }
}