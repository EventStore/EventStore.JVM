package eventstore
package j

import java.net.InetSocketAddress
import scala.concurrent.duration._

class ClusterSettingsBuilder extends Builder[ClusterSettings] with ChainSet[ClusterSettingsBuilder] {
  protected var _clusterDns          = ""
  protected var _maxDiscoverAttempts = 10
  protected var _externalGossipPort  = 30778
  protected var _gossipSeeds         = List.empty[InetSocketAddress]
  protected var _gossipTimeout       = 1.second

  def clusterDns(x: String): ClusterSettingsBuilder = set {
    require(x != null, "clusterDns must be not null")
    require(x.nonEmpty, "clusterDns must be not empty")
    _clusterDns = x
  }

  def maxDiscoverAttempts(x: Int): ClusterSettingsBuilder = set {
    _maxDiscoverAttempts = x
  }

  def externalGossipPort(x: Int): ClusterSettingsBuilder = set {
    _externalGossipPort = x
  }

  def gossipSeeds(x: InetSocketAddress*): ClusterSettingsBuilder = set {
    _gossipSeeds = x.toList
  }

  def gossipTimeout(x: FiniteDuration): ClusterSettingsBuilder = set {
    _gossipTimeout = x
  }

  def gossipTimeout(length: Long, unit: TimeUnit): ClusterSettingsBuilder = gossipTimeout(FiniteDuration(length, unit))

  def gossipTimeout(length: Long): ClusterSettingsBuilder = gossipTimeout(length, SECONDS)

  def build = ClusterSettings(
    clusterDns = _clusterDns,
    maxDiscoverAttempts = _maxDiscoverAttempts,
    externalGossipPort = _externalGossipPort,
    gossipSeeds = _gossipSeeds,
    gossipTimeout = _gossipTimeout)
}