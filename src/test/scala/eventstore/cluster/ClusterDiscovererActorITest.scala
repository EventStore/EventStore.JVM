package eventstore
package cluster

import akka.testkit.{ TestActorRef, TestActor }
import eventstore.cluster.ClusterDiscovererActor.{ GotNode, GetNode }
import eventstore.util.ActorSpec
import scala.concurrent.duration._

class ClusterDiscovererActorITest extends ActorSpec {
  "ClusterDiscovererActor" should {
    "discover cluster" in new TestScope {
      actor ! GetNode()
      val response = expectMsgType[GotNode](10.seconds)
      success
    }
  }

  trait TestScope extends ActorScope {
    val settings = ClusterSettings(GossipSeedsOrDns(
      "127.0.0.1" :: 1113,
      "127.0.0.1" :: 2113,
      "127.0.0.1" :: 3113))
    val actor = TestActorRef(ClusterDiscovererActor.props(settings))
  }
}