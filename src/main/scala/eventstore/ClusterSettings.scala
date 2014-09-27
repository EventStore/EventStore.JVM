package eventstore

import java.net.InetSocketAddress
import scala.concurrent.duration._

case class ClusterSettings(
    gossipSeedsOrDns: GossipSeedsOrDns,
    maxDiscoverAttempts: Int = 10,
    gossipTimeout: FiniteDuration = 1.second) {
  require(maxDiscoverAttempts >= 0, s"maxDiscoverAttempts must be >= 0, but is $maxDiscoverAttempts")
}

sealed trait GossipSeedsOrDns

object GossipSeedsOrDns {
  def apply(clusterDns: String): GossipSeedsOrDns = ClusterDns(clusterDns)

  def apply(clusterDns: String, externalGossipPort: Int): GossipSeedsOrDns = ClusterDns(clusterDns, externalGossipPort)

  def apply(gossipSeeds: InetSocketAddress*): GossipSeedsOrDns = GossipSeeds(gossipSeeds.toList)

  case class ClusterDns(clusterDns: String, externalGossipPort: Int = 30778) extends GossipSeedsOrDns {
    require(clusterDns != null, "clusterDns must be not null")
    require(clusterDns.nonEmpty, "clusterDns must be not empty")
  }

  case class GossipSeeds(gossipSeeds: List[InetSocketAddress]) extends GossipSeedsOrDns {
    require(gossipSeeds.nonEmpty, s"gossipSeeds must be non empty")
  }
}