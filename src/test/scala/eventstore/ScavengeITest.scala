package eventstore

class ScavengeITest extends TestConnection {
  sequential

  "scavenge" should {

    "scavenge database and fail if in progress" in new TestConnectionScope {
      actor ! ScavengeDatabase
      expectMsgType[ScavengeDatabaseResponse]
      actor ! ScavengeDatabase
      expectEsException() must throwA(ScavengeInProgressException)
    }

  }
}
