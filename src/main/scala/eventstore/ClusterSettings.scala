package eventstore

import java.net.InetSocketAddress
import scala.concurrent.duration._

case class ClusterSettings(
  clusterDns: String,
  maxDiscoverAttempts: Int = 10,
  externalGossipPort: Int = 30778,
  gossipSeeds: List[InetSocketAddress] = Nil /*TODO*/ ,
  gossipTimeout: FiniteDuration = 1.second) {
  require(clusterDns != null, "clusterDns must be not null")
  require(clusterDns.nonEmpty, "clusterDns must be not empty")
  require(maxDiscoverAttempts >= 0, s"maxDiscoverAttempts must be >= 0, but is $maxDiscoverAttempts")
}