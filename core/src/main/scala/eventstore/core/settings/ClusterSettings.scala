package eventstore
package core
package settings

import java.net.InetSocketAddress
import scala.concurrent.duration._
import ScalaCompat._
import com.typesafe.config.Config
import syntax._
import cluster.GossipSeedsOrDns

/**
 * Contains settings relating to a connection to a cluster.
 *
 * @param gossipSeedsOrDns Gossip seeds or DNS settings
 * @param dnsLookupTimeout The time given to resolve dns
 * @param maxDiscoverAttempts Maximum number of attempts for discovering endpoints
 * @param discoverAttemptInterval The interval between cluster discovery attempts
 * @param discoveryInterval The interval at which to keep discovering cluster
 * @param gossipTimeout Timeout for cluster gossip.
 */
@SerialVersionUID(1L)
final case class ClusterSettings(
    gossipSeedsOrDns:        GossipSeedsOrDns = GossipSeedsOrDns.GossipSeeds("127.0.0.1" :: 2113),
    dnsLookupTimeout:        FiniteDuration   = 2.seconds,
    maxDiscoverAttempts:     Int              = 10,
    discoverAttemptInterval: FiniteDuration   = 500.millis,
    discoveryInterval:       FiniteDuration   = 1.second,
    gossipTimeout:           FiniteDuration   = 1.second
) {
  require(maxDiscoverAttempts >= 1, s"maxDiscoverAttempts must be >= 1, but is $maxDiscoverAttempts")
}

object ClusterSettings {

  def opt(conf: Config): Option[ClusterSettings] = {
    def opt(conf: Config) = {
      def option[T](path: String, f: String => T): Option[T] = {
        if (conf hasPath path) Option(f(path)) else None
      }

      def clusterDns = option("dns", conf.getString).map { dns =>
        GossipSeedsOrDns(
          clusterDns = dns,
          externalGossipPort = conf getInt "external-gossip-port"
        )
      }

      def gossipSeeds = option("gossip-seeds", conf.getStringList).flatMap { ss =>
        if (ss.isEmpty) None
        else {
          val seeds = ss.toScala.map { s =>
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
          gossipSeedsOrDns =        gossipSeedsOrDns,
          dnsLookupTimeout =        conf duration "dns-lookup-timeout",
          maxDiscoverAttempts =     conf getInt "max-discover-attempts",
          discoverAttemptInterval = conf duration "discover-attempt-interval",
          discoveryInterval =       conf duration "discovery-interval",
          gossipTimeout =           conf duration "gossip-timeout"
        )
      }
    }
    opt(conf getConfig "eventstore.cluster")
  }
}