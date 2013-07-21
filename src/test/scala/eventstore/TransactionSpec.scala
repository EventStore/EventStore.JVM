package eventstore

import OperationResult._
import akka.testkit.TestProbe

/**
 * @author Yaroslav Klymko
 */
class TransactionSpec extends TestConnectionSpec {
  "transaction" should {
    "start on non existing stream with correct exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = doTransactionStart(NoStream)
      doTransactionWrite(newEvent)
      transactionCommit
    }

    "start on non existing stream with any exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = doTransactionStart(AnyVersion)
      doTransactionWrite(newEvent)
      transactionCommit
    }

    "fail to commit on non existing stream with wrong exp ver" in new TransactionScope {
      implicit val transactionId = doTransactionStart(EmptyStream)
      doTransactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "do nothing if commits without events to empty stream" in new TransactionScope {
      implicit val transactionId = doTransactionStart(NoStream)
      transactionCommit
      streamEvents must beEmpty
    }

    "do nothing if commits no events to empty stream" in new TransactionScope {
      implicit val transactionId = doTransactionStart(NoStream)
      doTransactionWrite()
      transactionCommit
      streamEvents must beEmpty
    }

    "validate expectations on commit" in new TransactionScope {
      implicit val transactionId = doTransactionStart(Version(1))
      doTransactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "commit when writing with exp ver ANY even while someone is writing in parallel" in new TransactionScope {
      appendEventToCreateStream()

      implicit val transactionId = doTransactionStart(AnyVersion)

      val probe = TestProbe()

      doAppendToStream(newEvent, AnyVersion, 1, probe)

      doTransactionWrite(newEvent)

      doAppendToStream(newEvent, AnyVersion, 2, probe)

      doTransactionWrite(newEvent)
      transactionCommit
      streamEvents must haveSize(5)
    }

    "fail to commit if started with correct ver but committing with bad" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = doTransactionStart(EmptyStream)
      doAppendToStream(newEvent, EmptyStream, 1)
      doTransactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "succeed to commit if started with wrong ver but committing with correct ver" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = doTransactionStart(Version(1))
      doAppendToStream(newEvent, EmptyStream, 1)
      doTransactionWrite()
      transactionCommit
    }

    "fail to commit if stream has been deleted during transaction" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = doTransactionStart(EmptyStream)
      deleteStream()
      failTransactionCommit(StreamDeleted)
    }

    "idempotency is correct for explicit transactions with expected version any" in new TransactionScope {
      val event = newEvent
      val transactionId1 = doTransactionStart(AnyVersion)
      doTransactionWrite(event)(transactionId1)
      transactionCommit(transactionId1)

      val transactionId2 = doTransactionStart(AnyVersion)
      doTransactionWrite(event)(transactionId2)
      transactionCommit(transactionId2)
      streamEvents mustEqual List(event)
    }
  }

  trait TransactionScope extends TestConnectionScope {

    def transactionWrite(events: Event*)(implicit transactionId: Long) =
      TransactionWrite(transactionId, events.toList, requireMaster = true)

    def transactionWriteCompleted(implicit transactionId: Long) = TransactionWriteCompleted(transactionId, Success, None)

    def doTransactionStart(expVer: ExpectedVersion = AnyVersion): Long = {
      actor ! TransactionStart(streamId, expVer, requireMaster = true)
      expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }
    }

    def doTransactionWrite(events: Event*)(implicit transactionId: Long) {
      actor ! transactionWrite(events: _*)
      expectMsg(transactionWriteCompleted)
    }

    def transactionCommit(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId, requireMaster = true)
      expectMsg(TransactionCommitCompleted(transactionId, Success, None))
    }

    def failTransactionCommit(result: Value)(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId, requireMaster = true)
      expectMsgPF() {
        case TransactionCommitCompleted(`transactionId`, `result`, Some(_)) => true
      }
    }
  }
}