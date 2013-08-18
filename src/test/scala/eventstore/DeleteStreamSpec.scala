package eventstore

import OperationFailed._
import ExpectedVersion._

/**
 * @author Yaroslav Klymko
 */
class DeleteStreamSpec extends TestConnectionSpec {
  "delete stream" should {
    "succeed if doesn't exist when passed ANY for expected version" in new DeleteStreamScope {
      deleteStream(Any)
    }

    "fail if doesn't exist and invalid expect version" in new DeleteStreamScope {
      failDeleteStream(ExpectedVersion(0)) mustEqual WrongExpectedVersion
      failDeleteStream(ExpectedVersion(1)) mustEqual WrongExpectedVersion
    }

    "succeed if correct expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(ExpectedVersion(0))
    }

    "succeed if any expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(Any)
    }

    "fail if invalid expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      failDeleteStream(ExpectedVersion(1)) mustEqual WrongExpectedVersion
    }

    "fail if already deleted" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(ExpectedVersion(0))
      failDeleteStream(ExpectedVersion(0)) mustEqual StreamDeleted
      failDeleteStream(Any) mustEqual StreamDeleted
      failDeleteStream(ExpectedVersion(1)) mustEqual StreamDeleted
    }
  }

  abstract class DeleteStreamScope extends TestConnectionScope {
    def failDeleteStream(expVer: ExpectedVersion.Existing = Any) = {
      actor ! DeleteStream(streamId, expVer, requireMaster = true)
      expectMsgPF() {
        case DeleteStreamFailed(reason, _) => reason
      }
    }
  }
}
