package eventstore
package tcp


import akka.util.{ByteIterator, ByteStringBuilder}

/**
 * @author Yaroslav Klymko
 */

object Flag extends Enumeration {
  val Auth = Value

  implicit class RichFlag(val self:Value) extends AnyVal{
    def byte: Byte = self match {
      case Auth => 0x01
    }
  }

  def deserialize(iterator: ByteIterator): Set[Value] = {
    val byte = iterator.getByte
    if ((byte & Auth.byte) == 0) Set() else Set(Auth)
  }

  def write(flags: Set[Value], builder: ByteStringBuilder) {
    val byte = if (flags.isEmpty) 0x00.toByte else Auth.byte
    builder.putByte(byte)
  }
}


sealed trait TcpPackage {
  def correlationId: Uuid
}


// TODO is auth optional ?
case class TcpPackageOut(correlationId: Uuid, message: Out, auth: Option[AuthData]) extends TcpPackage


object TcpPackageOut {
  def apply(message: Out): TcpPackageOut = TcpPackageOut(newUuid, message, None)
  def apply(correlationId: Uuid, message: Out): TcpPackageOut = TcpPackageOut(correlationId, message, None)
}

// TODO is auth optional ?
case class TcpPackageIn(correlationId: Uuid, message: In, auth: Option[AuthData]) extends TcpPackage

@deprecated()
case class AuthData(login: String, password: String) {
  require(login.nonEmpty, "login is empty")
  require(password.nonEmpty, "password is empty")
}

object AuthData {
  val defaultAdmin = AuthData("admin", "changeit")
}
