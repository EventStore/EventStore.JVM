package eventstore
package cluster

import java.net.InetSocketAddress
import util.ActorSpec
import scala.concurrent.Await
import scala.concurrent.duration._

// TODO Cluster Spec
class ClusterInfoITest extends ActorSpec {
  "ClusterInfo.future" should {
    "return ClusterInfo for address" in new ActorScope {

      val internal = new InetSocketAddress("127.0.0.1", 1113)
      val external = new InetSocketAddress("127.0.0.1", 1114)

      def clusterInfo(x: InetSocketAddress) = {
        val future = ClusterInfo.future(x)
        Await.result(future, 3.seconds)
      }

      val info = clusterInfo(internal)
      info.members must haveSize(3)

      val Some(master) = info.members.find(_.state == NodeState.Master)

      foreach(info.members) { member =>
        member.isAlive must beTrue
        member.nodePriority mustEqual 0
        member.externalSecureTcp.getPort mustEqual 0
        member.internalSecureTcp.getPort mustEqual 0
      }
    }
  }
}
