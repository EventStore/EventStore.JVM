package eventstore

import OperationFailed._

/**
 * @author Yaroslav Klymko
 */
class DeleteStreamSpec extends TestConnectionSpec {
  "delete stream" should {
    "succeed if doesn't exist when passed ANY for expected version" in new DeleteStreamScope {
      deleteStream(AnyVersion)
    }

    "fail if doesn't exist and invalid expect version" in new DeleteStreamScope {
      failDeleteStream(EmptyStream) mustEqual WrongExpectedVersion
      failDeleteStream(Version(1)) mustEqual WrongExpectedVersion
    }

    "succeed if correct expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(EmptyStream)
    }

    "succeed if any expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(AnyVersion)
    }

    "fail if invalid expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      failDeleteStream(NoStream) mustEqual WrongExpectedVersion
      failDeleteStream(Version(1)) mustEqual WrongExpectedVersion
    }

    "fail if already deleted" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(EmptyStream)
      failDeleteStream(EmptyStream) mustEqual StreamDeleted
      failDeleteStream(NoStream) mustEqual StreamDeleted
      failDeleteStream(AnyVersion) mustEqual StreamDeleted
      failDeleteStream(Version(1)) mustEqual StreamDeleted
    }
  }

  abstract class DeleteStreamScope extends TestConnectionScope {
    def failDeleteStream(expVer: ExpectedVersion = AnyVersion) = {
      actor ! DeleteStream(streamId, expVer, requireMaster = true)
      expectMsgPF() {
        case DeleteStreamFailed(reason, _) => reason
      }
    }
  }
}
