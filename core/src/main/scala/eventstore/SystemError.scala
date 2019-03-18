package eventstore

import java.net.InetSocketAddress
import scala.util.control.NoStackTrace

trait SystemError extends RuntimeException with NoStackTrace with Serializable

@SerialVersionUID(1L) case object OperationTimedOut extends SystemError

sealed trait ServerError extends SystemError

@SerialVersionUID(1L) case object BadRequest extends ServerError

@SerialVersionUID(1L) case object NotAuthenticated extends ServerError

sealed trait OperationError extends ServerError

object OperationError {
  @SerialVersionUID(1L) case object PrepareTimeout extends OperationError
  @SerialVersionUID(1L) case object CommitTimeout extends OperationError
  @SerialVersionUID(1L) case object ForwardTimeout extends OperationError
  @SerialVersionUID(1L) case object WrongExpectedVersion extends OperationError
  @SerialVersionUID(1L) case object StreamDeleted extends OperationError
  @SerialVersionUID(1L) case object InvalidTransaction extends OperationError
  @SerialVersionUID(1L) case object AccessDenied extends OperationError
}

sealed trait ReadEventError extends ServerError

object ReadEventError {
  @SerialVersionUID(1L) case object EventNotFound extends ReadEventError
  @SerialVersionUID(1L) case object StreamNotFound extends ReadEventError
  @SerialVersionUID(1L) case object StreamDeleted extends ReadEventError
  @SerialVersionUID(1L) case object AccessDenied extends ReadEventError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends ReadEventError {
    override def toString = s"ReadEventError($message)"
  }
}

sealed trait ReadStreamEventsError extends ServerError

object ReadStreamEventsError {
  @SerialVersionUID(1L) case object StreamNotFound extends ReadStreamEventsError
  @SerialVersionUID(1L) case object StreamDeleted extends ReadStreamEventsError
  @SerialVersionUID(1L) case object AccessDenied extends ReadStreamEventsError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends ReadStreamEventsError {
    override def toString = s"ReadStreamEventsError($message)"
  }
}

sealed trait ReadAllEventsError extends ServerError

object ReadAllEventsError {
  @SerialVersionUID(1L) case object AccessDenied extends ReadAllEventsError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends ReadAllEventsError {
    override def toString = s"ReadAllEventsError($message)"
  }
}

@SerialVersionUID(1L) final case class NotHandled(reason: NotHandled.Reason) extends ServerError {
  override def toString = s"NotHandled($reason)"
}

object NotHandled {
  sealed trait Reason

  @SerialVersionUID(1L) case object NotReady extends Reason
  @SerialVersionUID(1L) case object TooBusy extends Reason
  @SerialVersionUID(1L) final case class NotMaster(masterInfo: MasterInfo) extends Reason

  @SerialVersionUID(1L) final case class MasterInfo(
    tcpAddress:       InetSocketAddress,
    httpAddress:      InetSocketAddress,
    tcpSecureAddress: Option[InetSocketAddress] = None
  )
}

sealed trait SubscriptionDropped extends ServerError

object SubscriptionDropped {
  @SerialVersionUID(1L) case object AccessDenied extends SubscriptionDropped
  @SerialVersionUID(1L) case object NotFound extends SubscriptionDropped
  @SerialVersionUID(1L) case object PersistentSubscriptionDeleted extends SubscriptionDropped
  @SerialVersionUID(1L) case object SubscriberMaxCountReached extends SubscriptionDropped
}

sealed trait ScavengeError extends SystemError

object ScavengeError {
  @SerialVersionUID(1L) case object InProgress extends ScavengeError
  @SerialVersionUID(1L) case object Unauthorized extends ScavengeError
}

sealed trait CreatePersistentSubscriptionError extends SystemError

object CreatePersistentSubscriptionError {
  @SerialVersionUID(1L) case object AccessDenied extends CreatePersistentSubscriptionError
  @SerialVersionUID(1L) case object AlreadyExists extends CreatePersistentSubscriptionError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends CreatePersistentSubscriptionError {
    override def toString = s"CreatePersistentSubscriptionError($message)"
  }
}

sealed trait UpdatePersistentSubscriptionError extends SystemError

object UpdatePersistentSubscriptionError {
  @SerialVersionUID(1L) case object AccessDenied extends UpdatePersistentSubscriptionError
  @SerialVersionUID(1L) case object DoesNotExist extends UpdatePersistentSubscriptionError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends UpdatePersistentSubscriptionError {
    override def toString = s"UpdatePersistentSubscriptionError($message)"
  }
}

sealed trait DeletePersistentSubscriptionError extends SystemError

object DeletePersistentSubscriptionError {
  @SerialVersionUID(1L) case object AccessDenied extends DeletePersistentSubscriptionError
  @SerialVersionUID(1L) case object DoesNotExist extends DeletePersistentSubscriptionError
  @SerialVersionUID(1L) final case class Error(message: Option[String]) extends DeletePersistentSubscriptionError {
    override def toString = s"DeletePersistentSubscriptionError($message)"
  }
}