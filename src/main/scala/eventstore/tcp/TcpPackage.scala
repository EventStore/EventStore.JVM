package eventstore.tcp


import eventstore._
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

  def serialize(implicit ev: T <:< Out): Bytes = {
    val builder = ByteString.newBuilder
    write(builder)
    builder.result().toArray
  }

  def write(builder: ByteStringBuilder)(implicit ev: T <:< Out) {
    val (markerByte, bytes) = Serializers.serialize(message)
    builder.putByte(markerByte)

    val flags = auth.fold[Set[Flag.Value]](Set())(_ => Set(Flag.Auth))
    Flag.write(flags, builder)

    UuidSerializer.write(builder, correlationId)

    auth.foreach {
      case AuthData(login, password) =>
        builder.putByte(login.length.toByte)
        builder.append(ByteString(login))

        builder.putByte(password.length.toByte)
        builder.append(ByteString(password))
    }
    builder.putBytes(bytes)
  }
}


object TcpPackage {
  def apply[T <: Message](message: T): TcpPackage[T] = TcpPackage(newUuid, message, None)

  def apply[T <: Message](correlationId: Uuid, message: T): TcpPackage[T] = TcpPackage(correlationId, message, None)

  def deserialize(bs: ByteString): TcpPackage[In] = read(bs.iterator)

  def read(iterator: ByteIterator): TcpPackage[In] = {
    val markerByte = iterator.getByte
    val flags = Flag.deserialize(iterator)
    val correlationId = UuidSerializer.read(iterator)

    val authData = if (flags contains Flag.Auth) Some {
      val loginLength = iterator.getByte
      val login = iterator.take(loginLength).clone().toByteString.utf8String
      val passwordLength = iterator.getByte
      val password = iterator.take(passwordLength).clone().toByteString.utf8String
      AuthData(login, password)
    } else None

    val deserializers = Deserializers.deserialize(markerByte)
    val xx = iterator.toByteString
    val command = Message.deserialize(xx)(x => deserializers(x.asByteBuffer))
    TcpPackage(correlationId, command, authData)
  }
}
