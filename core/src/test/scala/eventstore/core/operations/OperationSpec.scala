package eventstore
package core
package operations

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

trait OperationSpec extends Specification {

  type Client = Unit

  protected trait OperationScope extends Scope {
    val client: Client = ()
  }
}
