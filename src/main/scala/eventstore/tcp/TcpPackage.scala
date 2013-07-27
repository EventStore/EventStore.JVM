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

case class TcpPackage[T <: Message](correlationId: Uuid, message: T, auth: Option[AuthData]) {

  def serialize(implicit ev: T <:< Out): ByteString = {
    val builder = ByteString.newBuilder
    write(builder)
    builder.result()
  }

  def write(builder: ByteStringBuilder)(implicit ev: T <:< Out) {
    val (markerByte, bytes) = Serializers.serialize(message)
    builder.putByte(markerByte)

    val flags = auth.fold[Set[Flag.Value]](Set())(_ => Set(Flag.Auth))
    Flag.write(flags, builder)

    UuidSerializer.write(builder, correlationId)

    auth.foreach(_.write(builder))
    builder.putBytes(bytes)
  }
}


object TcpPackage {
  def apply[T <: Message](message: T): TcpPackage[T] = TcpPackage(newUuid, message, None)

  def apply[T <: Message](correlationId: Uuid, message: T): TcpPackage[T] = TcpPackage(correlationId, message, None)

  def deserialize(bs: ByteString): TcpPackage[In] = read(bs.iterator)

  def read(iterator: ByteIterator): TcpPackage[In] = {
    val deserializer = Deserializers.deserialize(iterator.getByte)
    val flags = Flag.deserialize(iterator)
    val correlationId = UuidSerializer.read(iterator)

    val authData = if (flags contains Flag.Auth) Some(AuthData.read(iterator)) else None

    val message = deserializer(iterator.toByteString)
    TcpPackage(correlationId, message, authData)
  }
}

case class AuthData(login: String, password: String) {
  require(login.nonEmpty, "login is empty")
  require(password.nonEmpty, "password is empty")

  def write(builder: ByteStringBuilder) {
    def putString(s: String) {
      val bs = ByteString(s)
      builder.putByte(bs.size.toByte)
      builder.append(bs)
    }
    putString(login)
    putString(password)
  }
}

object AuthData {
  val defaultAdmin = AuthData("admin", "changeit")

  def read(iterator: ByteIterator): AuthData = {
    def getString = {
      val length = iterator.getByte
      val bytes = new Bytes(length)
      iterator.getBytes(bytes)
      new String(bytes, "UTF-8")
    }
    val login = getString
    val password = getString
    AuthData(login, password)
  }
}
