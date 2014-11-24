package eventstore

import eventstore.tcp.PackOut

import scala.util.control.NoStackTrace

abstract class EsException(message: String) extends RuntimeException(message) with NoStackTrace {
  def this() = this(null)
}

case class StreamNotFoundException(streamId: EventStream.Id) extends EsException(s"$streamId not found")

class StreamDeletedException(message: String) extends EsException(message)

class AccessDeniedException(message: String) extends EsException(message)

case object InvalidTransactionException extends EsException

case class WrongExpectedVersionException(message: String) extends EsException(message)

class ServerErrorException(message: String) extends EsException(message)

case class EventNotFoundException(streamId: EventStream.Id, number: EventNumber)
  extends EsException(s"No event found in $streamId at $number")

case class NotAuthenticatedException(pack: PackOut) extends EsException(s"Authentication error for $pack")

case class NonMetadataEventException(event: Event) extends EsException(s"Non metadata event $event")

/**
 * OperationTimeoutException
 * @param pack Outgoing pack, to which response timed out
 */
case class OperationTimeoutException private[eventstore] (pack: PackOut)
  extends EsException(s"Operation hasn't got response from server for $pack")

case object ScavengeInProgressException extends EsException

class ScavengeFailedException(message: String) extends EsException(message)

class CommandNotExpectedException(message: String) extends EsException(message)