package eventstore

import OperationFailed._
import akka.testkit.TestProbe


/**
 * @author Yaroslav Klymko
 */
class TransactionSpec extends TestConnectionSpec {
  implicit val direction = ReadDirection.Forward
  "transaction" should {
    "start on non existing stream with correct exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = transactionStart(NoStream)
      transactionWrite(newEvent)
      transactionCommit
    }

    "start on non existing stream with any exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = transactionStart(AnyVersion)
      transactionWrite(newEvent)
      transactionCommit
    }

    "fail to commit on non existing stream with wrong exp ver" in new TransactionScope {
      implicit val transactionId = transactionStart(EmptyStream)
      transactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "do nothing if commits without events to empty stream" in new TransactionScope {
      implicit val transactionId = transactionStart(NoStream)
      transactionCommit
      readStreamEventsFailed.reason mustEqual ReadStreamEventsFailed.NoStream
    }

    "do nothing if commits no events to empty stream" in new TransactionScope {
      implicit val transactionId = transactionStart(NoStream)
      transactionWrite()
      transactionCommit
      readStreamEventsFailed.reason mustEqual ReadStreamEventsFailed.NoStream
    }

    "validate expectations on commit" in new TransactionScope {
      implicit val transactionId = transactionStart(Version(1))
      transactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "commit when writing with exp ver ANY even while someone is writing in parallel" in new TransactionScope {
      appendEventToCreateStream()

      implicit val transactionId = transactionStart(AnyVersion)

      val probe = TestProbe()

      doAppendToStream(newEvent, AnyVersion, 1, probe)

      transactionWrite(newEvent)

      doAppendToStream(newEvent, AnyVersion, 2, probe)

      transactionWrite(newEvent)
      transactionCommit
      streamEvents must haveSize(5)
    }

    "fail to commit if started with correct ver but committing with bad" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(EmptyStream)
      doAppendToStream(newEvent, EmptyStream, 1)
      transactionWrite(newEvent)
      failTransactionCommit(WrongExpectedVersion)
    }

    "succeed to commit if started with wrong ver but committing with correct ver" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(Version(1))
      doAppendToStream(newEvent, EmptyStream, 1)
      transactionWrite()
      transactionCommit
    }

    "fail to commit if stream has been deleted during transaction" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(EmptyStream)
      deleteStream()
      failTransactionCommit(StreamDeleted)
    }

    "idempotency is correct for explicit transactions with expected version any" in new TransactionScope {
      val event = newEvent
      val transactionId1 = transactionStart(AnyVersion)
      transactionWrite(event)(transactionId1)
      transactionCommit(transactionId1)

      val transactionId2 = transactionStart(AnyVersion)
      transactionWrite(event)(transactionId2)
      transactionCommit(transactionId2)
      streamEvents mustEqual List(event)
    }
  }

  trait TransactionScope extends TestConnectionScope {

    def transactionStart(expVer: ExpectedVersion = AnyVersion): Long = {
      actor ! TransactionStart(streamId, expVer, requireMaster = true)
      expectMsgType[TransactionStartSucceed].transactionId
    }

    def transactionWrite(events: Event*)(implicit transactionId: Long) {
      actor ! TransactionWrite(transactionId, events.toList, requireMaster = true)
      expectMsg(TransactionWriteSucceed(transactionId))
    }

    def transactionCommit(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId, requireMaster = true)
      expectMsg(TransactionCommitSucceed(transactionId))
    }

    def failTransactionCommit(result: Value)(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId, requireMaster = true)
      expectMsgPF() {
        case TransactionCommitFailed(`transactionId`, `result`, Some(_)) => true
      }
    }
  }
}