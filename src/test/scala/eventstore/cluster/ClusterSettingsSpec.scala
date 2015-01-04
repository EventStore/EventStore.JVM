package eventstore
package cluster

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import eventstore.cluster.GossipSeedsOrDns._

class ClusterSettingsSpec extends Specification with util.NoConversions {
  lazy val config = ConfigFactory.load()
  "ClusterSettings" should {
    "return None by default" in {
      ClusterSettings.opt(config) must beNone
    }

    "return Some if dns specified" in {
      val conf = ConfigFactory
        .parseString("""eventstore.cluster.dns = "localhost" """)
        .withFallback(config)
      ClusterSettings.opt(conf) must beSome(ClusterSettings(ClusterDns("localhost")))
    }

    "return Some if gossip seeds specified" in {
      val conf = ConfigFactory
        .parseString("""eventstore.cluster.gossip-seeds = ["127.0.0.1:1", "127.0.0.2:2"] """)
        .withFallback(config)
      ClusterSettings.opt(conf) must beSome(ClusterSettings(GossipSeeds(List(
        "127.0.0.1" :: 1,
        "127.0.0.2" :: 2))))
    }

    "return Some with dns if both gossip seeds and dns specified" in {
      val conf = ConfigFactory
        .parseString(
          """eventstore.cluster {
            |   dns = "localhost"
            |   gossip-seeds = ["127.0.0.1:1", "127.0.0.2:2"]
            |}
          """.stripMargin)
        .withFallback(config)
      ClusterSettings.opt(conf) must beSome(ClusterSettings(ClusterDns("localhost")))
    }
  }
}