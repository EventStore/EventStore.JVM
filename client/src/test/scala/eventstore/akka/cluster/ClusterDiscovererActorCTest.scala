package eventstore
package akka
package cluster

import scala.concurrent.duration._
import eventstore.core.syntax._
import eventstore.cluster.ClusterSettings
import eventstore.cluster.GossipSeedsOrDns.GossipSeeds
import ClusterDiscovererActor.{Address, GetAddress}

class ClusterDiscovererActorCTest extends ActorSpec {
  "ClusterDiscovererActor" should {
    "discover cluster" in new TestScope {
      actor ! GetAddress()
      expectMsgType[Address](3.seconds)
    }
  }

  trait TestScope extends ActorScope {
    val seeds = GossipSeeds(
      "127.0.0.1" :: 1113,
      "127.0.0.1" :: 2113,
      "127.0.0.1" :: 3113
    )
    val settings = ClusterSettings(seeds)
    val actor = system.actorOf(ClusterDiscovererActor.props(settings, ClusterInfoOf.apply))
  }
}