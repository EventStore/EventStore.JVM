package eventstore
package core
package cluster

import scala.collection.immutable.SortedSet

sealed trait NodeState extends Ordered[NodeState] {
  def id: Int
  def isAllowedToConnect: Boolean
  def compare(that: NodeState) = this.id compare that.id
}

object NodeState {
  val values: SortedSet[NodeState] = SortedSet(
    Initializing,
    ReadOnlyLeaderless,
    DiscoverLeader,
    Unknown,
    PreReadOnlyReplica,
    PreReplica,
    CatchingUp,
    Clone,
    ReadOnlyReplica,
    Follower,
    PreLeader,
    Leader,
    Manager,
    ShuttingDown,
    Shutdown,
    ResigningLeader
  )

  final val oldTerminology: Map[String, NodeState] = Map(
    "Slave"     -> NodeState.Follower,
    "Master"    -> NodeState.Leader,
    "PreMaster" -> NodeState.PreLeader
  )

  private val map: Map[String, NodeState] =
    values.map(x => x.toString -> x).toMap ++ oldTerminology

  def apply(x: String): NodeState =
    map.getOrElse(x, throw new IllegalArgumentException(s"No NodeState found for $x"))

  // id value and order derived from:
  // https://github.com/EventStore/EventStoreDB-Client-Dotnet-Legacy/blob/24cacecb6f003df5015258b05d8356e12c6b694f/src/EventStore.ClientAPI/Messages/ClusterMessages.cs#L74

  @SerialVersionUID(1L) case object Initializing extends NodeState {
    def id = 0
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object ReadOnlyLeaderless extends NodeState {
    def id = 1
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object DiscoverLeader extends NodeState {
    def id = 2
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object Unknown extends NodeState {
    def id = 3
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object PreReadOnlyReplica extends NodeState {
    def id = 4
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object PreReplica extends NodeState {
    def id = 5
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object CatchingUp extends NodeState {
    def id = 6
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object Clone extends NodeState {
    def id = 7
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object ReadOnlyReplica extends NodeState {
    def id = 8
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object Follower extends NodeState {
    def id = 9
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object PreLeader extends NodeState {
    def id = 10
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object Leader extends NodeState {
    def id = 11
    def isAllowedToConnect = true
  }

  @SerialVersionUID(1L) case object Manager extends NodeState {
    def id = 12
    def isAllowedToConnect = false
  }

  @SerialVersionUID(1L) case object ShuttingDown extends NodeState {
    def id = 13
    def isAllowedToConnect = false
  }

  @SerialVersionUID(1L) case object Shutdown extends NodeState {
    def id = 14
    def isAllowedToConnect = false
  }

   @SerialVersionUID(1L) case object ResigningLeader extends NodeState {
    def id = 15
    def isAllowedToConnect = false
  }

}