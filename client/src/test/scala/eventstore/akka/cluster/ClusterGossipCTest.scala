package eventstore
package akka
package cluster

import eventstore.core.syntax._
import eventstore.core.cluster.NodeState

class ClusterGossipCTest extends ActorSpec {

  "ClusterInfoOf" should {

    "return ClusterInfo for address" in new ActorScope {

      val futureFunc = ClusterInfoOf.apply
      val info = futureFunc("127.0.0.1" :: 2114).await_
      info.members must haveSize(3)

      info.members.find(_.state == NodeState.Leader) must beSome

      foreach(info.members) { member =>
        member.isAlive must beTrue
        member.nodePriority mustEqual 0
        member.externalSecureTcp.getPort mustEqual 0
        member.internalSecureTcp.getPort mustEqual 0
      }
    }
  }
}
