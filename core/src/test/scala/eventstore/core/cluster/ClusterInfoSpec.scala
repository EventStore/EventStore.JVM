package eventstore
package core
package cluster

import java.time.ZonedDateTime
import scala.collection.immutable.SortedSet
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import syntax._
import util.uuid.randomUuid
import NodeState._

class ClusterInfoSpec extends Specification {

  "ClusterInfo.bestNode" should {

    "return Leader if exists" in new TestScope {
      val expected = member(Leader)
      val clusterInfo = ClusterInfo(address, membersFrom(expected))
      clusterInfo.bestNode must beSome(expected)
    }

    "return PreLeader if exists & Leader not present" in new TestScope {
      val expected = member(PreLeader)
      val clusterInfo = ClusterInfo(address, membersFrom(expected, Leader))
      clusterInfo.bestNode must beSome(expected)
    }

    "return Follower if exists & Leader/PreLeader not present" in new TestScope {
      val expected = member(Follower)
      val clusterInfo = ClusterInfo(address, membersFrom(expected, Leader, PreLeader))
      clusterInfo.bestNode must beSome(expected)
    }

    "return None if empty" in new TestScope {
      ClusterInfo(address, Nil).bestNode must beNone
    }

    "return None if no alive nodes" in new TestScope {
      ClusterInfo(address, List(
        member(isAlive = false),
        member(isAlive = false)
      )).bestNode must beNone
    }

    "return None if no node with proper state" in new TestScope {
      ClusterInfo(address, List(
        member(ShuttingDown),
        member(Shutdown),
        member(Manager)
      )).bestNode must beNone
    }
  }

  trait TestScope extends Scope {

    val address = "127.0.0.1" :: 1

    def membersFrom(expected: MemberInfo, exclude: NodeState*) =
      expected :: (NodeState.values -- SortedSet(expected.state +: exclude:_*)).map(x => member(x)).toList

    def member(state: NodeState = NodeState.Leader, isAlive: Boolean = true) = MemberInfo(
      instanceId = randomUuid,
      timestamp = ZonedDateTime.now,
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
      nodePriority = 0
    )
  }
}
