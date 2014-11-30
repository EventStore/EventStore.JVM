package eventstore

import java.net.InetSocketAddress
import scala.util.control.NoStackTrace

trait SystemError extends RuntimeException with NoStackTrace with Serializable

case object OperationTimedOut extends SystemError

sealed trait ServerError extends SystemError

case object BadRequest extends ServerError // TODO check use case

case object NotAuthenticated extends ServerError // TODO check use case

sealed trait OperationError extends ServerError

object OperationError {
  case object PrepareTimeout extends OperationError
  case object CommitTimeout extends OperationError
  case object ForwardTimeout extends OperationError
  case object WrongExpectedVersion extends OperationError
  case object StreamDeleted extends OperationError
  case object InvalidTransaction extends OperationError
  case object AccessDenied extends OperationError
}

sealed trait ReadEventError extends ServerError

object ReadEventError {
  case object EventNotFound extends ReadEventError
  case object StreamNotFound extends ReadEventError
  case object StreamDeleted extends ReadEventError
  case class Error(message: Option[String]) extends ReadEventError
  case object AccessDenied extends ReadEventError
}

sealed trait ReadStreamEventsError extends ServerError

object ReadStreamEventsError {
  case object StreamNotFound extends ReadStreamEventsError
  case object StreamDeleted extends ReadStreamEventsError
  case class Error(message: Option[String]) extends ReadStreamEventsError
  case object AccessDenied extends ReadStreamEventsError
}

sealed trait ReadAllEventsError extends ServerError

object ReadAllEventsError {
  case class Error(message: Option[String]) extends ReadAllEventsError
  case object AccessDenied extends ReadAllEventsError
}

case class NotHandled(reason: NotHandled.Reason) extends ServerError

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

sealed trait SubscriptionDropped extends ServerError

object SubscriptionDropped {
  case object AccessDenied extends SubscriptionDropped
}

sealed trait ScavengeError extends SystemError

object ScavengeError {
  case object InProgress extends ScavengeError
  case class Failed(error: Option[String]) extends ScavengeError
}