package eventstore

import scala.concurrent.duration._
import akka.testkit.TestProbe

class ScavengeITest extends TestConnection {
  "scavenge" should {
    "scavenge database" in new TestConnectionScope {
      actor ! ScavengeDatabase

      val probe = TestProbe()
      actor.tell(ScavengeDatabase, probe.ref)

      expectMsgType[ScavengeDatabaseCompleted]

      probe.expectMsgPF(5.seconds) {
        case EsException(_: EsError.ScavengeInProgress, msg) => msg
      } must beSome
    }.pendingUntilFixed
  }
}
