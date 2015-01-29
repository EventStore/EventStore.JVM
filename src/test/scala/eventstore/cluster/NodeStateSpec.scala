package eventstore.cluster

import org.specs2.mutable.Specification
import eventstore.cluster.NodeState._

class NodeStateSpec extends Specification {
  "NodeState" should {
    "throw exception for illegal string" in {
      NodeState("test") must throwAn[IllegalArgumentException]
    }
  }

  "NodeState.isReplica" should {
    "return true for CatchingUp, Clone, Slave" in {
      val replicas = Set(CatchingUp, Clone, Slave)
      val other = NodeState.values -- replicas

      foreach(replicas) { state =>
        state.isReplica must beTrue
      }

      foreach(other) { state =>
        state.isReplica must beFalse
      }
    }
  }

  "NodeState.isAllowedToConnect" should {
    "return false for Manager, ShuttingDown, Shutdown" in {
      val replicas = Set(Manager, ShuttingDown, Shutdown)
      val other = NodeState.values -- replicas

      foreach(replicas) { state =>
        state.isAllowedToConnect must beFalse
      }

      foreach(other) { state =>
        state.isAllowedToConnect must beTrue
      }
    }
  }
}