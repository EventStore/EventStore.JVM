package eventstore
package core
package cluster

import org.specs2.mutable.Specification
import NodeState._

class NodeStateSpec extends Specification {

  "NodeState" should {
    "throw exception for illegal string" in {
      NodeState("test") must throwAn[IllegalArgumentException]
    }
  }

  "NodeState.isAllowedToConnect" should {

    "return false for Manager, ShuttingDown, Shutdown" in {

      val notAllowedStates = Set(Manager, ShuttingDown, Shutdown)
      val other = NodeState.values -- notAllowedStates

      foreach(notAllowedStates) { state =>
        state.isAllowedToConnect must beFalse
      }

      foreach(other) { state =>
        state.isAllowedToConnect must beTrue
      }
    }

  }
}