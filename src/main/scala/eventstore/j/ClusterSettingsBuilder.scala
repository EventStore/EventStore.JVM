package eventstore
package j

import java.net.InetSocketAddress
import cluster.ClusterSettings
import cluster.GossipSeedsOrDns
import cluster.GossipSeedsOrDns.{ GossipSeeds, ClusterDns }
import scala.concurrent.duration._

class ClusterSettingsBuilder extends Builder[ClusterSettings] with ChainSet[ClusterSettingsBuilder] {
  private val default = ClusterSettings()
  protected var _gossipSeedsOrDns: GossipSeedsOrDns = default.gossipSeedsOrDns
  protected var _dnsLookupTimeout = default.dnsLookupTimeout
  protected var _maxDiscoverAttempts = default.maxDiscoverAttempts
  protected var _discoverAttemptInterval = default.discoverAttemptInterval
  protected var _discoveryInterval = default.discoveryInterval
  protected var _gossipTimeout = default.gossipTimeout

  def clusterDns(dns: String): ClusterSettingsBuilder = set { _gossipSeedsOrDns = ClusterDns(dns) }

  def clusterDns(dns: String, port: Int): ClusterSettingsBuilder = set { _gossipSeedsOrDns = ClusterDns(dns, port) }

  def gossipSeeds(seeds: InetSocketAddress*): ClusterSettingsBuilder = set {
    _gossipSeedsOrDns = GossipSeeds(seeds.toList)
  }

  def dnsLookupTimeout(x: FiniteDuration): ClusterSettingsBuilder = set { _dnsLookupTimeout = x }

  def dnsLookupTimeout(length: Long, unit: TimeUnit): ClusterSettingsBuilder = dnsLookupTimeout(FiniteDuration(length, unit))

  def dnsLookupTimeout(seconds: Long): ClusterSettingsBuilder = dnsLookupTimeout(seconds, SECONDS)

  def maxDiscoverAttempts(x: Int): ClusterSettingsBuilder = set { _maxDiscoverAttempts = x }

  def discoverAttemptInterval(x: FiniteDuration): ClusterSettingsBuilder = set { _discoverAttemptInterval = x }

  def discoverAttemptInterval(length: Long, unit: TimeUnit): ClusterSettingsBuilder = discoverAttemptInterval(FiniteDuration(length, unit))

  def discoverAttemptInterval(seconds: Long): ClusterSettingsBuilder = discoverAttemptInterval(seconds, SECONDS)

  def discoveryInterval(x: FiniteDuration): ClusterSettingsBuilder = set { _discoveryInterval = x }

  def discoveryInterval(length: Long, unit: TimeUnit): ClusterSettingsBuilder = discoveryInterval(FiniteDuration(length, unit))

  def discoveryInterval(seconds: Long): ClusterSettingsBuilder = discoveryInterval(seconds, SECONDS)

  def gossipTimeout(x: FiniteDuration): ClusterSettingsBuilder = set { _gossipTimeout = x }

  def gossipTimeout(length: Long, unit: TimeUnit): ClusterSettingsBuilder = gossipTimeout(FiniteDuration(length, unit))

  def gossipTimeout(seconds: Long): ClusterSettingsBuilder = gossipTimeout(seconds, SECONDS)

  def build: ClusterSettings = ClusterSettings(
    gossipSeedsOrDns = _gossipSeedsOrDns,
    dnsLookupTimeout = _dnsLookupTimeout,
    maxDiscoverAttempts = _maxDiscoverAttempts,
    discoverAttemptInterval = _discoverAttemptInterval,
    discoveryInterval = _discoveryInterval,
    gossipTimeout = _gossipTimeout)
}