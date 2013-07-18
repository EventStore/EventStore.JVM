package eventstore

import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class DeleteStreamSpec extends TestConnectionSpec {
  "delete stream" should {
    "succeed if doesn't exist when passed ANY for expected version" in new DeleteStreamScope {
      deleteStream(AnyVersion)
    }

    "fail if doesn't exist and invalid expect version" in new DeleteStreamScope {
      failDeleteStream(EmptyStream, WrongExpectedVersion)
      failDeleteStream(Version(1), WrongExpectedVersion)
    }

    "succeed if correct expected version" in new DeleteStreamScope {
      createStream()
      deleteStream(EmptyStream)
    }

    "succeed if any expected version" in new DeleteStreamScope {
      createStream()
      deleteStream(AnyVersion)
    }

    "fail if invalid expected version" in new DeleteStreamScope {
      createStream()
      failDeleteStream(NoStream, WrongExpectedVersion)
      failDeleteStream(Version(1), WrongExpectedVersion)
    }

    "fail if already deleted" in new DeleteStreamScope {
      createStream()
      deleteStream(EmptyStream)
      failDeleteStream(EmptyStream, StreamDeleted)
      failDeleteStream(NoStream, StreamDeleted)
      failDeleteStream(AnyVersion, StreamDeleted)
      failDeleteStream(Version(1), StreamDeleted)
    }
  }

  abstract class DeleteStreamScope extends TestConnectionScope {
    def failDeleteStream(expVer: ExpectedVersion = AnyVersion, result: Value) {
      actor ! DeleteStream(streamId, expVer, requireMaster = true)
      expectMsgPF() {
        case DeleteStreamCompleted(`result`, Some(_)) => true
      }
    }
  }
}
