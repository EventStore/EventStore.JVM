package eventstore
package core
package tcp

import scala.util.Try

sealed trait Pack {
  def correlationId: Uuid
}

@SerialVersionUID(1L)
final case class PackIn(
  message: Try[In],
  correlationId: Uuid
) extends Pack

@SerialVersionUID(1L)
final case class PackOut(
  message:       Out,
  correlationId: Uuid,
  credentials:   Option[UserCredentials] = None
) extends Pack