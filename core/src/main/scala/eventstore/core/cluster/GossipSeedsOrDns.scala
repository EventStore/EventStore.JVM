package eventstore
package core
package cluster

import java.net.InetSocketAddress

sealed trait GossipSeedsOrDns

object GossipSeedsOrDns {

  def apply(clusterDns: String): GossipSeedsOrDns                          = ClusterDns(clusterDns)
  def apply(clusterDns: String, externalGossipPort: Int): GossipSeedsOrDns = ClusterDns(clusterDns, externalGossipPort)
  def apply(gossipSeeds: InetSocketAddress*): GossipSeedsOrDns             = GossipSeeds(gossipSeeds.toList)

  /**
   * Used if we're discovering via DNS
   *
   * @param clusterDns The DNS name to use for discovering endpoints.
   * @param externalGossipPort The well-known endpoint on which cluster managers are running.
   */
  @SerialVersionUID(1L)
  final case class ClusterDns(clusterDns: String, externalGossipPort: Int) extends GossipSeedsOrDns {
    require(clusterDns != null, "clusterDns must not be null")
    require(clusterDns.nonEmpty, "clusterDns must not be empty")
    require(0 < externalGossipPort && externalGossipPort < 65536, s"externalGossipPort is not valid :$externalGossipPort")
  }

  object ClusterDns {

    def apply(clusterDns: String = "localhost", externalGossipPort: Int = 30778): ClusterDns =
      new ClusterDns(clusterDns, externalGossipPort)

  }

  /**
   * Used if we're connecting with gossip seeds
   *
   * @param gossipSeeds Endpoints for seeding gossip.
   */
  @SerialVersionUID(1L)
  final case class GossipSeeds(gossipSeeds: List[InetSocketAddress]) extends GossipSeedsOrDns {
    require(gossipSeeds.nonEmpty, s"gossipSeeds must be non empty")
  }

  object GossipSeeds {
    def apply(gossipSeeds: InetSocketAddress*): GossipSeeds = GossipSeeds(gossipSeeds.toList)
  }
}