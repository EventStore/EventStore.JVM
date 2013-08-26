package eventstore

import OperationFailed._
import ExpectedVersion._
import akka.testkit.TestProbe

/**
 * @author Yaroslav Klymko
 */
class TransactionITest extends TestConnection {
  implicit val direction = ReadDirection.Forward
  "transaction" should {
    "start on non existing stream with correct exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = transactionStart(NoStream)
      transactionWrite(newEventData)
      transactionCommit
    }

    "start on non existing stream with any exp ver and create stream on commit" in new TransactionScope {
      implicit val transactionId = transactionStart(Any)
      transactionWrite(newEventData)
      transactionCommit
    }

    "fail to commit on non existing stream with wrong exp ver" in new TransactionScope {
      implicit val transactionId = transactionStart(ExpectedVersion.First)
      transactionWrite(newEventData)
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
      implicit val transactionId = transactionStart(ExpectedVersion(1))
      transactionWrite(newEventData)
      failTransactionCommit(WrongExpectedVersion)
    }

    "commit when writing with exp ver ANY even while someone is writing in parallel" in new TransactionScope {
      appendEventToCreateStream()

      implicit val transactionId = transactionStart(Any)

      val probe = TestProbe()

      appendToStreamSucceed(Seq(newEventData), testKit = probe)

      transactionWrite(newEventData)

      appendToStreamSucceed(Seq(newEventData), testKit = probe)

      transactionWrite(newEventData)
      transactionCommit
      streamEvents must haveSize(5)
    }

    "fail to commit if started with correct ver but committing with bad" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(ExpectedVersion.First)
      append(newEventData).number mustEqual EventNumber(1)
      transactionWrite(newEventData)
      failTransactionCommit(WrongExpectedVersion)
    }

    "succeed to commit if started with wrong ver but committing with correct ver" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(ExpectedVersion(1))
      append(newEventData).number mustEqual EventNumber(1)
      transactionWrite()
      transactionCommit
    }

    "fail to commit if stream has been deleted during transaction" in new TransactionScope {
      appendEventToCreateStream()
      implicit val transactionId = transactionStart(ExpectedVersion.First)
      deleteStream()
      failTransactionCommit(StreamDeleted)
    }

    "idempotency is correct for explicit transactions with expected version any" in new TransactionScope {
      val event = newEventData
      val transactionId1 = transactionStart(Any)
      transactionWrite(event)(transactionId1)
      transactionCommit(transactionId1)

      val transactionId2 = transactionStart(Any)
      transactionWrite(event)(transactionId2)
      transactionCommit(transactionId2)
      streamEvents mustEqual List(event)
    }
  }

  trait TransactionScope extends TestConnectionScope {

    def transactionStart(expVer: ExpectedVersion = Any): Long = {
      actor ! TransactionStart(streamId, expVer)
      expectMsgType[TransactionStartSucceed].transactionId
    }

    def transactionWrite(events: EventData*)(implicit transactionId: Long) {
      actor ! TransactionWrite(transactionId, events.toList)
      expectMsg(TransactionWriteSucceed(transactionId))
    }

    def transactionCommit(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId)
      expectMsg(TransactionCommitSucceed(transactionId))
    }

    def failTransactionCommit(result: Value)(implicit transactionId: Long) {
      actor ! TransactionCommit(transactionId)
      expectMsgPF() {
        case TransactionCommitFailed(`transactionId`, `result`, Some(_)) => true
      }
    }
  }
}