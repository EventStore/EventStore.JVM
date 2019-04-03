package eventstore
package core

import java.net.InetSocketAddress
import scala.util.control.NoStackTrace

trait SystemError extends RuntimeException with NoStackTrace with Serializable

case object OperationTimedOut extends SystemError

sealed trait ServerError extends SystemError

case object BadRequest       extends ServerError
case object NotAuthenticated extends ServerError

sealed trait OperationError extends ServerError

object OperationError {
  case object PrepareTimeout       extends OperationError
  case object CommitTimeout        extends OperationError
  case object ForwardTimeout       extends OperationError
  case object WrongExpectedVersion extends OperationError
  case object StreamDeleted        extends OperationError
  case object InvalidTransaction   extends OperationError
  case object AccessDenied         extends OperationError
}

sealed trait ReadEventError extends ServerError

object ReadEventError {
  case object EventNotFound  extends ReadEventError
  case object StreamNotFound extends ReadEventError
  case object StreamDeleted  extends ReadEventError
  case object AccessDenied   extends ReadEventError
  final case class Error(message: Option[String]) extends ReadEventError {
    override def toString = s"ReadEventError($message)"
  }
}

sealed trait ReadStreamEventsError extends ServerError

object ReadStreamEventsError {
  case object StreamNotFound extends ReadStreamEventsError
  case object StreamDeleted  extends ReadStreamEventsError
  case object AccessDenied   extends ReadStreamEventsError
  final case class Error(message: Option[String]) extends ReadStreamEventsError {
    override def toString = s"ReadStreamEventsError($message)"
  }
}

sealed trait ReadAllEventsError extends ServerError

object ReadAllEventsError {
  case object AccessDenied extends ReadAllEventsError
  final case class Error(message: Option[String]) extends ReadAllEventsError {
    override def toString = s"ReadAllEventsError($message)"
  }
}

final case class NotHandled(reason: NotHandled.Reason) extends ServerError {
  override def toString = s"NotHandled($reason)"
}

object NotHandled {
  sealed trait Reason

  case object NotReady                       extends Reason
  case object TooBusy                        extends Reason
  final case class NotMaster(mi: MasterInfo) extends Reason

  final case class MasterInfo(
    tcpAddress:       InetSocketAddress,
    httpAddress:      InetSocketAddress,
    tcpSecureAddress: Option[InetSocketAddress]
  )
  object MasterInfo {
    def apply(tcpAddress: InetSocketAddress, httpAddress: InetSocketAddress): MasterInfo =
      MasterInfo(tcpAddress, httpAddress, None)
  }
}

sealed trait SubscriptionDropped extends ServerError

object SubscriptionDropped {
  case object AccessDenied                  extends SubscriptionDropped
  case object NotFound                      extends SubscriptionDropped
  case object PersistentSubscriptionDeleted extends SubscriptionDropped
  case object SubscriberMaxCountReached     extends SubscriptionDropped
}

sealed trait ScavengeError extends SystemError

object ScavengeError {
  case object InProgress   extends ScavengeError
  case object Unauthorized extends ScavengeError
}

sealed trait CreatePersistentSubscriptionError extends SystemError

object CreatePersistentSubscriptionError {
  case object AccessDenied  extends CreatePersistentSubscriptionError
  case object AlreadyExists extends CreatePersistentSubscriptionError
  final case class Error(message: Option[String]) extends CreatePersistentSubscriptionError {
    override def toString = s"CreatePersistentSubscriptionError($message)"
  }
}

sealed trait UpdatePersistentSubscriptionError extends SystemError

object UpdatePersistentSubscriptionError {
  case object AccessDenied extends UpdatePersistentSubscriptionError
  case object DoesNotExist extends UpdatePersistentSubscriptionError
  final case class Error(message: Option[String]) extends UpdatePersistentSubscriptionError {
    override def toString = s"UpdatePersistentSubscriptionError($message)"
  }
}

sealed trait DeletePersistentSubscriptionError extends SystemError

object DeletePersistentSubscriptionError {
  case object AccessDenied extends DeletePersistentSubscriptionError
  case object DoesNotExist extends DeletePersistentSubscriptionError
  final case class Error(message: Option[String]) extends DeletePersistentSubscriptionError {
    override def toString = s"DeletePersistentSubscriptionError($message)"
  }
}