package eventstore
package tcp

import scala.util.Try

/**
 * @author Yaroslav Klymko
 */
sealed trait TcpPackage {
  def correlationId: Uuid
}

case class TcpPackageIn(correlationId: Uuid, message: Try[In]) extends TcpPackage

case class TcpPackageOut(correlationId: Uuid, message: Out, credentials: Option[UserCredentials]) extends TcpPackage

object TcpPackageOut {
  def apply(message: Out): TcpPackageOut = TcpPackageOut(newUuid, message, None)

  def apply(correlationId: Uuid, message: Out): TcpPackageOut = TcpPackageOut(correlationId, message, None)
}