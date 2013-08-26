package eventstore
package tcp

/**
 * @author Yaroslav Klymko
 */
sealed trait TcpPackage {
  def correlationId: Uuid
}

case class TcpPackageIn(correlationId: Uuid, message: In) extends TcpPackage

case class TcpPackageOut(correlationId: Uuid, message: Out, userCredentials: Option[UserCredentials]) extends TcpPackage

object TcpPackageOut {
  def apply(message: Out): TcpPackageOut = TcpPackageOut(newUuid, message, None)
  def apply(correlationId: Uuid, message: Out): TcpPackageOut = TcpPackageOut(correlationId, message, None)
}