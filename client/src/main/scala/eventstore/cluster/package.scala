package eventstore

// TODO(AHJ): Remove this package object after 7.1.0

package object cluster {

  import eventstore.core.{settings => cs}
  import eventstore.core.{cluster => cc}

  @deprecated(deprecationMsg("GossipSeedsOrDns", "eventstore.cluster", "eventstore.core.cluster"), since = sinceVersion)
  type GossipSeedsOrDns = cc.GossipSeedsOrDns
  val  GossipSeedsOrDns = cc.GossipSeedsOrDns

  @deprecated(deprecationMsg("ClusterSettings", "eventstore.cluster", "eventstore.core.settings"), since = sinceVersion)
  type ClusterSettings  = cs.ClusterSettings
  val  ClusterSettings  = cs.ClusterSettings

}
