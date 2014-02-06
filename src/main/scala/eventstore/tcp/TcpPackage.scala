package eventstore
package tcp

import scala.util.Try

sealed trait TcpPackage {
  def correlationId: Uuid
}

case class TcpPackageIn(message: Try[In], correlationId: Uuid = randomUuid) extends TcpPackage

case class TcpPackageOut(
  message: Out,
  correlationId: Uuid = randomUuid,
  credentials: Option[UserCredentials] = None) extends TcpPackage