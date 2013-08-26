package eventstore

/**
 * @author Yaroslav Klymko
 */
object OperationFailed extends Enumeration {
  val PrepareTimeout, CommitTimeout, ForwardTimeout, WrongExpectedVersion, StreamDeleted, InvalidTransaction, AccessDenied = Value
}