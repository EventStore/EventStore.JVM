package eventstore
package operations

import tcp.PackOut
import akka.actor.ActorRef
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.util.Try

trait OperationSpec extends Specification with Mockito {

  protected trait OperationScope extends Scope {
    val inFunc = mock[InFunc]
    val outFunc = mock[OutFunc]
    val client: ActorRef = null

    def pack: PackOut

    def thereWasStop(x: Try[In]): Unit = {
      there was one(inFunc).apply(x)
      there were noCallsTo(outFunc)
    }
  }
}
