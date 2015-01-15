package eventstore.cluster

import eventstore.util.NoConversions
import org.specs2.mutable.Specification
import scala.concurrent.duration._

class ResolveDnsSpec extends Specification with NoConversions {
  val timeout = 500.millis

  "ResolveDns" should {
    "resolve yahoo.com" in {
      ResolveDns("yahoo.com", timeout) must not be empty
    }

    "resolve google.com" in {
      ResolveDns("google.com", timeout) must not be empty
    }

    "not resolve test" in {
      ResolveDns("test", timeout) must throwAn[ClusterException]
    }
  }
}
