package eventstore

object EventStoreError extends Enumeration {
  val EventNotFound = Value
  val StreamNotFound = Value
  val PrepareTimeout = Value
  val CommitTimeout = Value
  val ForwardTimeout = Value
  val WrongExpectedVersion = Value
  val StreamDeleted = Value
  val InvalidTransaction = Value
  val AccessDenied = Value
  val Error = Value
}

case class EventStoreException(
  reason: EventStoreError.Value,
  message: Option[String] = None,
  cause: Option[Throwable] = None) extends Exception(message getOrElse null, cause getOrElse null)