package eventstore
package tcp

import scala.util.Try
import eventstore.util.uuid.randomUuid

sealed trait Pack {
  def correlationId: Uuid
}

@SerialVersionUID(1L)
final case class PackIn(message: Try[In], correlationId: Uuid = randomUuid) extends Pack

object PackIn {
  def apply(message: In): PackIn = PackIn(Try(message))
}

@SerialVersionUID(1L)
final case class PackOut(
  message:       Out,
  correlationId: Uuid                    = randomUuid,
  credentials:   Option[UserCredentials] = None
) extends Pack