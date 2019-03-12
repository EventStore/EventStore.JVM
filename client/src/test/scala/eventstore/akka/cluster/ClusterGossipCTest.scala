package eventstore
package akka
package cluster

import eventstore.syntax._
import eventstore.cluster.NodeState

class ClusterGossipCTest extends ActorSpec {

  "ClusterInfoOf" should {

    "return ClusterInfo for address" in new ActorScope {

      val futureFunc = ClusterInfoOf.apply
      val info = futureFunc("127.0.0.1" :: 1113).await_
      info.members must haveSize(3)

      info.members.find(_.state == NodeState.Master) must beSome

      foreach(info.members) { member =>
        member.isAlive must beTrue
        member.nodePriority mustEqual 0
        member.externalSecureTcp.getPort mustEqual 0
        member.internalSecureTcp.getPort mustEqual 0
      }
    }
  }
}
