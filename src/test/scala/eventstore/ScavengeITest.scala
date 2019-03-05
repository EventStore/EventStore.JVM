package eventstore

import akka.testkit.TestProbe

class ScavengeITest extends TestConnection {
  sequential

  "scavenge" should {
    "scavenge database" in new TestConnectionScope {
      actor ! ScavengeDatabase
      expectMsgType[ScavengeDatabaseResponse]
    }

    "fail if scavenge is in progress" in new TestConnectionScope {
      val probe = TestProbe()
      actor.tell(ScavengeDatabase, probe.ref)
      actor ! ScavengeDatabase
      expectEsException() must throwA(ScavengeInProgressException)

      probe.expectMsgType[ScavengeDatabaseResponse]
    }
  }
}
