package eventstore

import java.net.InetSocketAddress

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
  case object BadRequest extends EsError

  case class NotHandled(reason: NotHandled.Reason) extends EsError

  object NotHandled {
    sealed trait Reason

    case object NotReady extends Reason
    case object TooBusy extends Reason
    case class NotMaster(masterInfo: MasterInfo) extends Reason

    case class MasterInfo(
      tcpAddress: InetSocketAddress,
      httpAddress: InetSocketAddress,
      tcpSecureAddress: Option[InetSocketAddress] = None)
  }

  // TODO should we contain required data ?
  case class ScavengeInProgress(totalTimeMs: Int, totalSpaceSaved: Long) extends EsError
  case class ScavengeFailed(totalTimeMs: Int, totalSpaceSaved: Long) extends EsError
}

case class EsException(reason: EsError, message: Option[String] = None)
    extends Exception(message.orNull, null, false, false) {

  override def toString = {
    val body = message.fold(reason.toString)(x => s"$reason, $x")
    s"EsException($body)"
  }
}