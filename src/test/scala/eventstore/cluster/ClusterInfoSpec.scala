package eventstore
package cluster

import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import eventstore.cluster.NodeState._

class ClusterInfoSpec extends Specification {
  "ClusterInfo.bestNode" should {
    "return Master if exists" in new TestScope {
      val expected = member(Master)
      val clusterInfo = ClusterInfo(address, expected :: (NodeState.values - Master).map(x => member(x)).toList)
      clusterInfo.bestNode must beSome(expected)
    }

    "return None if empty" in new TestScope {
      ClusterInfo(address, Nil).bestNode must beNone
    }

    "return None if no alive nodes" in new TestScope {
      ClusterInfo(address, List(
        member(isAlive = false),
        member(isAlive = false))).bestNode must beNone
    }

    "return None if no node with proper state" in new TestScope {
      ClusterInfo(address, List(
        member(ShuttingDown),
        member(Shutdown),
        member(Manager))).bestNode must beNone
    }
  }

  trait TestScope extends Scope {
    val address = "127.0.0.1" :: 1

    def member(state: NodeState = NodeState.Master, isAlive: Boolean = true) = MemberInfo(
      instanceId = randomUuid,
      timestamp = DateTime.now,
      state = state,
      isAlive = isAlive,
      internalTcp = address,
      externalTcp = address,
      internalSecureTcp = address,
      externalSecureTcp = address,
      internalHttp = address,
      externalHttp = address,
      lastCommitPosition = 0,
      writerCheckpoint = 0,
      chaserCheckpoint = 0,
      epochPosition = 0,
      epochNumber = 0,
      epochId = randomUuid,
      nodePriority = 0)
  }
}
