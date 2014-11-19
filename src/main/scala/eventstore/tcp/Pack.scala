package eventstore
package tcp

import scala.util.Try

sealed trait Pack {
  def correlationId: Uuid
}

case class PackIn(message: Try[In], correlationId: Uuid = randomUuid) extends Pack

case class PackOut(
  message: Out,
  correlationId: Uuid = randomUuid,
  credentials: Option[UserCredentials] = None) extends Pack