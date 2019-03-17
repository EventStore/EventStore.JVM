package eventstore
package cluster

import org.specs2.mutable.Specification
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ResolveDnsSpec extends Specification {
  val timeout = 2.seconds

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
