package eventstore
package cluster

class ClusterInfoITest extends util.ActorSpec {
  "ClusterInfo.futureFunc" should {
    "return ClusterInfo for address" in new ActorScope {
      val futureFunc = ClusterInfo.futureFunc
      val info = futureFunc("127.0.0.1" :: 1113).await_ // TODO
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
