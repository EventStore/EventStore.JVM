package eventstore

import eventstore.tcp.PackOut

import scala.util.control.NoStackTrace

abstract class EsException(message: String, cause: Throwable)
    extends RuntimeException(message, cause) with NoStackTrace {

  def this() = this(null, null)

  def this(message: String) = this(message, null)
}

@SerialVersionUID(1L) class CannotEstablishConnectionException(message: String, cause: Throwable)
    extends EsException(message, cause) {

  def this(message: String) = this(message, null)
}

@SerialVersionUID(1L)
case class StreamNotFoundException(streamId: EventStream.Id) extends EsException(s"$streamId not found")

@SerialVersionUID(1L) class StreamDeletedException(message: String) extends EsException(message)

@SerialVersionUID(1L) class AccessDeniedException(message: String) extends EsException(message)

@SerialVersionUID(1L) case object InvalidTransactionException extends EsException

@SerialVersionUID(1L) class WrongExpectedVersionException(message: String) extends EsException(message)

@SerialVersionUID(1L) class ServerErrorException(message: String) extends EsException(message)

@SerialVersionUID(1L) case class EventNotFoundException(streamId: EventStream.Id, number: EventNumber)
  extends EsException(s"No event found in $streamId at $number")

@SerialVersionUID(1L) case class NotAuthenticatedException(pack: PackOut) extends EsException(s"Authentication error for $pack")

@SerialVersionUID(1L) case class NonMetadataEventException(event: Event) extends EsException(s"Non metadata event $event")

/**
 * OperationTimeoutException
 * @param pack Outgoing pack, to which response timed out
 */
@SerialVersionUID(1L) case class OperationTimeoutException private[eventstore] (pack: PackOut)
  extends EsException(s"Operation hasn't got response from server for $pack")

@SerialVersionUID(1L) case object ScavengeInProgressException extends EsException
@SerialVersionUID(1L) case object ScavengeUnauthorizedException extends EsException

@SerialVersionUID(1L) class CommandNotExpectedException(message: String) extends EsException(message)

@SerialVersionUID(1L) class RetriesLimitReachedException(message: String) extends EsException(message)

@SerialVersionUID(1L) class ProjectionException(message: String, cause: Throwable) extends EsException(message, cause) {
  def this(message: String) = this(message, null)
}

@SerialVersionUID(1L) class InvalidOperationException(message: String) extends EsException(message)