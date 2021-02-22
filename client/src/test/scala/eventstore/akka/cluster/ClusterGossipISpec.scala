package eventstore
package akka
package cluster

import scala.util.Try
import eventstore.core.syntax._
import eventstore.core.cluster.NodeState
import eventstore.akka.testutil.isES20Series

class ClusterGossipITest extends ActorSpec {

  "ClusterInfoOf - ES20 Series" should {

    "return ClusterInfo for single node using tls" in new ActorScope {

      if(isES20Series) {

        val futureFunc = ClusterInfoOf(useTls = true)
        val port = sys.env.get("ES_TEST_HTTP_PORT").flatMap(p => Try(p.toInt).toOption).getOrElse(2113)
        val info = futureFunc("127.0.0.1" :: port).await_

        info.members must haveSize(1)
        info.members.find(_.state == NodeState.Leader) must beSome

        foreach(info.members) { member =>
            member.isAlive must beTrue
            member.nodePriority mustEqual 0
            member.externalSecureTcp.getPort mustEqual 1113
            member.internalSecureTcp.getPort mustEqual 1112
            member.externalHttp.getPort() mustEqual 2113
            member.internalHttp.getPort() mustEqual 2113
        }

      } else ok

    }
  }

}