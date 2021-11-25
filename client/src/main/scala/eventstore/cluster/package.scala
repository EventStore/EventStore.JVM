package eventstore

// TODO(AHJ): Remove this package object after 7.1.0

package object cluster {

  import eventstore.core.{settings => cs}
  import eventstore.core.{cluster => cc}

  private final val clusterMsg =
    "This type has been moved from eventstore.cluster to eventstore.core.cluster. " +
    "Please update your imports, as this deprecated type alias will " +
    "be removed in a future version of EventStore.JVM."

  @deprecated(clusterMsg, "7.0.0")
  type GossipSeedsOrDns = cc.GossipSeedsOrDns
  val  GossipSeedsOrDns = cc.GossipSeedsOrDns

  @deprecated(clusterMsg, "7.0.0")
  type ClusterSettings  = cs.ClusterSettings
  val  ClusterSettings  = cs.ClusterSettings

}
