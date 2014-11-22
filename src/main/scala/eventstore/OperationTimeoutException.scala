package eventstore

import eventstore.tcp.PackOut
import scala.util.control.NoStackTrace

/**
 * OperationTimeoutException
 * @param pack Outgoing pack, to which response timed out
 */
case class OperationTimeoutException private[eventstore] (pack: PackOut)
  extends IEsException(s"Operation hasn't got response from server for $pack")
  with NoStackTrace