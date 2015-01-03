package eventstore
package operations

import akka.actor.ActorRef
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

trait OperationSpec extends Specification {

  protected trait OperationScope extends Scope {
    val client: ActorRef = null
  }
}
