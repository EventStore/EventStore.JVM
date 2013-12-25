package eventstore

// TODO rename to EsError
// TODO use Object
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
  val NotAuthenticated = Value
  val Error = Value
}

case class EventStoreException( // TODO rename to EsException
    reason: EventStoreError.Value,
    message: Option[String] = None,
    cause: Option[Throwable] = None) extends Exception(message getOrElse null, cause getOrElse null) {

  override def toString = {
    val body = message.fold(reason.toString)(x => s"$reason, $x")
    s"EventStoreException($body)"
  }
}