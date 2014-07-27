package eventstore.cluster

sealed trait NodeState {
  def isReplica: Boolean
}

object NodeState {

  private val map: Map[String, NodeState] = Set(
    Initializing,
    Unknown,
    PreReplica,
    CatchingUp,
    Clone,
    Slave,
    PreMaster,
    Master,
    Manager,
    ShuttingDown,
    Shutdown).map(x => x.toString -> x).toMap

  def apply(x: String): NodeState = map.getOrElse(x, sys.error(s"No NodeState found for $x"))

  case object Initializing extends NodeState { def isReplica = false }
  case object Unknown extends NodeState { def isReplica = false }
  case object PreReplica extends NodeState { def isReplica = false }
  case object CatchingUp extends NodeState { def isReplica = true }
  case object Clone extends NodeState { def isReplica = true }
  case object Slave extends NodeState { def isReplica = true }
  case object PreMaster extends NodeState { def isReplica = false }
  case object Master extends NodeState { def isReplica = false }
  case object Manager extends NodeState { def isReplica = false }
  case object ShuttingDown extends NodeState { def isReplica = false }
  case object Shutdown extends NodeState { def isReplica = false }
}