package eventstore.cluster

sealed trait NodeState {
  def isReplica: Boolean
}

object NodeState {
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