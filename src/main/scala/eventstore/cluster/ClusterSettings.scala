package eventstore
package cluster

import java.net.InetSocketAddress
import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Contains settings relating to a connection to a cluster.
 *
 * @param gossipSeedsOrDns Gossip seeds or DNS settings
 * @param maxDiscoverAttempts The maximum number of attempts for discovering endpoints.
 * @param gossipTimeout Timeout for cluster gossip.
 */
case class ClusterSettings(
    gossipSeedsOrDns: GossipSeedsOrDns,
    maxDiscoverAttempts: Int = 10,
    gossipTimeout: FiniteDuration = 1.second) {
  require(maxDiscoverAttempts >= 0, s"maxDiscoverAttempts must be >= 0, but is $maxDiscoverAttempts")
}

object ClusterSettings {
  def opt(config: Config): Option[ClusterSettings] = {
    def opt(config: Config) = {
      def option[T](path: String, f: String => T): Option[T] = {
        if (config hasPath path) Option(f(path)) else None
      }

      def clusterDns = option("dns", config.getString).map { dns =>
        GossipSeedsOrDns(
          clusterDns = dns,
          externalGossipPort = config getInt "external-gossip-port")
      }

      def gossipSeeds = option("gossip-seeds", config.getStringList).flatMap { ss =>
        if (ss.isEmpty) None
        else {
          val seeds = ss.asScala.map { s =>
            s.split(":") match {
              case Array(host, port) => new InetSocketAddress(host, port.toInt)
              case _                 => sys.error(s"Cannot parse address from $s, expected format is host:port")
            }
          }
          Some(GossipSeedsOrDns.GossipSeeds(seeds.toList))
        }
      }

      (clusterDns orElse gossipSeeds).map { gossipSeedsOrDns =>
        ClusterSettings(
          gossipSeedsOrDns = gossipSeedsOrDns,
          maxDiscoverAttempts = config getInt "max-discover-attempts",
          gossipTimeout = FiniteDuration(config.getDuration("gossip-timeout", MILLISECONDS), MILLISECONDS))
      }
    }
    opt(config getConfig "eventstore.cluster")
  }
}

sealed trait GossipSeedsOrDns

object GossipSeedsOrDns {
  def apply(clusterDns: String): GossipSeedsOrDns = ClusterDns(clusterDns)

  def apply(clusterDns: String, externalGossipPort: Int): GossipSeedsOrDns = ClusterDns(clusterDns, externalGossipPort)

  def apply(gossipSeeds: InetSocketAddress*): GossipSeedsOrDns = GossipSeeds(gossipSeeds.toList)

  /**
   * Used if we're discovering via DNS
   *
   * @param clusterDns The DNS name to use for discovering endpoints.
   * @param externalGossipPort The well-known endpoint on which cluster managers are running.
   */
  case class ClusterDns(
      clusterDns: String,
      externalGossipPort: Int = 30778) extends GossipSeedsOrDns {
    require(clusterDns != null, "clusterDns must be not null")
    require(clusterDns.nonEmpty, "clusterDns must be not empty")
  }

  /**
   * Used if we're connecting with gossip seeds
   *
   * @param gossipSeeds Endpoints for seeding gossip.
   */
  case class GossipSeeds(gossipSeeds: List[InetSocketAddress]) extends GossipSeedsOrDns {
    require(gossipSeeds.nonEmpty, s"gossipSeeds must be non empty")
  }
}