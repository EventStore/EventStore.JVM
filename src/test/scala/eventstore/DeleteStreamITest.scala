package eventstore

import ExpectedVersion._

class DeleteStreamITest extends TestConnection {
  "delete stream" should {
    "succeed if doesn't exist when passed ANY for expected version" in new DeleteStreamScope {
      deleteStream(Any)
    }

    "fail if doesn't exist and invalid expect version" in new DeleteStreamScope {
      deleteStreamFailed(ExpectedVersion.First) must throwA[WrongExpectedVersionException]
      deleteStreamFailed(ExpectedVersion.Exact(1)) must throwA[WrongExpectedVersionException]
    }

    "succeed if correct expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(ExpectedVersion.First)
    }

    "succeed if any expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(Any)
    }

    "fail if invalid expected version" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStreamFailed(ExpectedVersion.Exact(1)) must throwA[WrongExpectedVersionException]
    }

    "fail if already deleted" in new DeleteStreamScope {
      appendEventToCreateStream()
      deleteStream(ExpectedVersion.First)
      deleteStreamFailed(ExpectedVersion.First) must throwA[StreamDeletedException]
      deleteStreamFailed(Any) must throwA[StreamDeletedException]
      deleteStreamFailed(ExpectedVersion.Exact(1)) must throwA[StreamDeletedException]
    }
  }

  private abstract class DeleteStreamScope extends TestConnectionScope {
    def deleteStreamFailed(expVer: ExpectedVersion.Existing = Any) = {
      actor ! DeleteStream(streamId, hard = true, expectedVersion = expVer)
      expectEsException()
    }
  }
}
