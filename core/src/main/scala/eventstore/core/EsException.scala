package eventstore
package core

import scala.util.control.NoStackTrace
import tcp.PackOut
import EventStream.Id

abstract class EsException(message: String, cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull) with NoStackTrace

@SerialVersionUID(1L)
final case class CannotEstablishConnectionException(message: String, cause: Option[Throwable]) extends EsException(message, cause)
object CannotEstablishConnectionException {
  def apply(message: String): CannotEstablishConnectionException                = CannotEstablishConnectionException(message, None)
  def apply(message: String, th: Throwable): CannotEstablishConnectionException = CannotEstablishConnectionException(message, Option(th))
}

@SerialVersionUID(1L) final case class StreamNotFoundException(streamId: Id)           extends EsException(s"$streamId not found")
@SerialVersionUID(1L) final case class StreamDeletedException(message: String)         extends EsException(message)
@SerialVersionUID(1L) final case class AccessDeniedException(message: String)          extends EsException(message)
@SerialVersionUID(1L) case object      InvalidTransactionException                     extends EsException("Invalid transaction")
@SerialVersionUID(1L) final case class WrongExpectedVersionException(message: String)  extends EsException(message)
@SerialVersionUID(1L) final case class ServerErrorException(message: String)           extends EsException(message)
@SerialVersionUID(1L) final case class EventNotFoundException(id: Id, nr: EventNumber) extends EsException(s"No event found in $id at $nr")
@SerialVersionUID(1L) final case class NotAuthenticatedException(pack: PackOut)        extends EsException(s"Authentication error for $pack")
@SerialVersionUID(1L) final case class NonMetadataEventException(event: Event)         extends EsException(s"Non metadata event $event")
@SerialVersionUID(1L) final case class OperationTimeoutException(pack: PackOut)        extends EsException(s"Operation hasn't got response from server for $pack")
@SerialVersionUID(1L) case object      ScavengeInProgressException                     extends EsException("Scavenge already in progress")
@SerialVersionUID(1L) case object      ScavengeUnauthorizedException                   extends EsException("Not authorized to scavenge")
@SerialVersionUID(1L) final case class CommandNotExpectedException(message: String)    extends EsException(message)
@SerialVersionUID(1L) final case class RetriesLimitReachedException(message: String)   extends EsException(message)
@SerialVersionUID(1L) final case class InvalidOperationException(message: String)      extends EsException(message)