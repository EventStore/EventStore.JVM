package eventstore.client

import OperationResult._
import akka.testkit.TestProbe

/**
 * @author Yaroslav Klymko
 */
class TransactionSpec extends TestConnectionSpec {
  "transaction" should {
    "start on non existing stream with correct exp ver and create stream on commit" in new TransactionScope {
      actor ! transactionStart(NoStream)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsg(transactionCommitCompleted)
    }

    "start on non existing stream with any exp ver and create stream on commit" in new TransactionScope {
      actor ! transactionStart(AnyVersion)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsg(transactionCommitCompleted)
    }

    "fail to commit non existing stream with wrong exp ver" in new TransactionScope {
      actor ! transactionStart(EmptyStream)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsgPF() {
        case TransactionCommitCompleted(`transactionId`, WrongExpectedVersion, Some(_)) => true
      }
    }

    "create stream if commits no events to non existing stream" in new TransactionScope {
      actor ! transactionStart(NoStream)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! transactionWrite()
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsg(transactionCommitCompleted)

      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, _, _, _, _, _) => events
      } must haveSize(1)
    }

    "validate expectations on commit" in new TransactionScope {
      actor ! transactionStart(Version(1))
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsgPF() {
        case TransactionCommitCompleted(`transactionId`, WrongExpectedVersion, Some(_)) => true
      }
    }

    "commit when writing with exp ver ANY even while someone is writing in parallel" in new TransactionScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! transactionStart(AnyVersion)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      val probe = TestProbe()

      actor.!(writeEvents(AnyVersion, newEvent))(probe.ref)
      actor ! transactionWrite(newEvent)
      actor.!(writeEvents(AnyVersion, newEvent))(probe.ref)
      actor ! transactionWrite(newEvent)

      probe.expectMsgPF() {
        case WriteEventsCompleted(Success, None, _) => true
      }
      expectMsg(transactionWriteCompleted)
      probe.expectMsgPF() {
        case WriteEventsCompleted(Success, None, _) => true
      }
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsg(transactionCommitCompleted)

      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(xs, ReadStreamResult.Success, _, _, _, _, _) => xs
      } must haveSize(5)
    }

    "fail to commit if started with correct ver but committing with bad" in new TransactionScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! transactionStart(EmptyStream)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! writeEvents(EmptyStream, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsgPF() {
        case TransactionCommitCompleted(`transactionId`, WrongExpectedVersion, Some(_)) => true
      }
    }

    "succeed to commit if started with wrong ver but committing with correct ver" in new TransactionScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! transactionStart(Version(1))
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! writeEvents(EmptyStream, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! transactionWrite(newEvent)
      expectMsg(transactionWriteCompleted)

      actor ! transactionCommit
      expectMsg(transactionCommitCompleted)
    }

    "fail to commit if stream has been deleted during transaction" in new TransactionScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! transactionStart(EmptyStream)
      implicit val transactionId = expectMsgPF() {
        case TransactionStartCompleted(x, Success, None) => x
      }

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! transactionCommit
      expectMsgPF() {
        case TransactionCommitCompleted(`transactionId`, StreamDeleted, Some(_)) => true
      }
    }
  }

  trait TransactionScope extends TestConnectionScope {
    def transactionStart(expVer: ExpectedVersion) = TransactionStart(streamId, expVer, allowForwarding = true)

    def transactionWrite(events: NewEvent*)(implicit transactionId: Long) =
      TransactionWrite(transactionId, events.toList, allowForwarding = true)

    def transactionWriteCompleted(implicit transactionId: Long) = TransactionWriteCompleted(transactionId, Success, None)

    def transactionCommit(implicit transactionId: Long) = TransactionCommit(transactionId, allowForwarding = true)

    def transactionCommitCompleted(implicit transactionId: Long) = TransactionCommitCompleted(transactionId, Success, None)
  }
}
