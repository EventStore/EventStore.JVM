package eventstore
package core
package cluster

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import syntax._
import settings.ClusterSettings
import core.cluster.GossipSeedsOrDns._

class ClusterSettingsSpec extends Specification {
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
        "127.0.0.2" :: 2
      ))))
    }

    "return Some with dns if both gossip seeds and dns specified" in {
      val conf = ConfigFactory
        .parseString(
          """eventstore.cluster {
            |   dns = "localhost"
            |   gossip-seeds = ["127.0.0.1:1", "127.0.0.2:2"]
            |}
          """.stripMargin
        )
        .withFallback(config)
      ClusterSettings.opt(conf) must beSome(ClusterSettings(ClusterDns("localhost")))
    }

    "throw an exception if maxDiscoverAttempts < 1" in {
      ClusterSettings(maxDiscoverAttempts = -1) must throwAn[IllegalArgumentException]
    }

    "throw if gossip-seeds are not parseable" in {
      val conf = ConfigFactory
        .parseString("""eventstore.cluster.gossip-seeds = ["127.0.0.1", "127.0.0.2:2"] """)
        .withFallback(config)
      ClusterSettings.opt(conf) must throwAn[RuntimeException]
    }
  }

  "GossipSeedsOrDns" should {
    "return ClusterDns" in {
      GossipSeedsOrDns("localhost") mustEqual ClusterDns("localhost")
    }

    "return GossipSeeds" in {
      GossipSeedsOrDns("localhost" :: 1) mustEqual GossipSeeds(List("localhost" :: 1))
    }
  }

  "ClusterDns" should {
    "throw an exception if clusterDns is not valid" in {
      ClusterDns(clusterDns = null) must throwAn[IllegalArgumentException]
      ClusterDns(clusterDns = "") must throwAn[IllegalArgumentException]
    }

    "throw an exception if externalGossipPort is not valid" in {
      ClusterDns(externalGossipPort = 0) must throwAn[IllegalArgumentException]
    }
  }

  "GossipSeeds" should {
    "throw an exception gossipSeeds is empty" in {
      GossipSeeds(Nil) must throwAn[IllegalArgumentException]
    }
  }
}