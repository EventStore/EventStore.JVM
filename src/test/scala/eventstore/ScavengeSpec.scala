package eventstore

/**
 * @author Yaroslav Klymko
 */
class ScavengeSpec extends TestConnectionSpec {
  "scavenge" should {
    "scavenge database" in new TestConnectionScope {
      actor ! ScavengeDatabase
    }
  }
}
