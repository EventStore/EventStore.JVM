package eventstore

sealed trait EsError

object EsError {

  case object EventNotFound extends EsError
  case object StreamNotFound extends EsError
  case object PrepareTimeout extends EsError
  case object CommitTimeout extends EsError
  case object ForwardTimeout extends EsError
  case object WrongExpectedVersion extends EsError
  case object StreamDeleted extends EsError
  case object InvalidTransaction extends EsError
  case object AccessDenied extends EsError
  case object NotAuthenticated extends EsError
  case object Error extends EsError
  case object ConnectionLost extends EsError
}

case class EsException(reason: EsError, message: Option[String] = None)
    extends Exception(message getOrElse null, null, false, false) {

  override def toString = {
    val body = message.fold(reason.toString)(x => s"$reason, $x")
    s"EsException($body)"
  }
}