package eventstore
package tcp

import util.{BytesWriter, BytesReader, BytesFormat}
import akka.util.{ByteStringBuilder, ByteIterator}

/**
 * @author Yaroslav Klymko
 */
object EventStoreFormats extends EventStoreFormats

trait EventStoreFormats extends EventStoreProtoFormats {
  abstract class EmptyFormat[T](obj: T) extends BytesFormat[T] {
    def read(bi: ByteIterator) = obj
    def write(x: T, builder: ByteStringBuilder) {}
  }

  implicit object HeartbeatRequestCommandFormat extends EmptyFormat(HeartbeatRequestCommand)
  implicit object HeartbeatResponseCommandFormat extends EmptyFormat(HeartbeatResponseCommand)
  implicit object PingFormat extends EmptyFormat(Ping)
  implicit object PongFormat extends EmptyFormat(Pong)
  implicit object BadRequestFormat extends EmptyFormat(BadRequest)
  implicit object UnsubscribeFromStreamFormat extends EmptyFormat(UnsubscribeFromStream)
  implicit object ScavengeDatabaseFormat extends EmptyFormat(ScavengeDatabase)

  implicit object UserCredentialsFormat extends BytesFormat[UserCredentials] {
    def write(x: UserCredentials, builder: ByteStringBuilder) {
      def putString(s: String, name: String) {
        val bs = ByteString(s)
        val length = bs.length
        require(length < 256, s"$name serialized length should be less than 256 bytes, but is $length:$x")
        builder.putByte(bs.size.toByte)
        builder.append(bs)
      }
      putString(x.login, "login")
      putString(x.password, "password")
    }

    def read(bi: ByteIterator) = {
      def getString = {
        val length = bi.getByte
        val bytes = new Bytes(length)
        bi.getBytes(bytes)
        new String(bytes, "UTF-8")
      }
      val login = getString
      val password = getString
      UserCredentials(login, password)
    }
  }

  implicit object FlagsFormat extends BytesFormat[Flags] {
    def write(x: Flags, builder: ByteStringBuilder) {
      builder.putByte(x)
    }

    def read(bi: ByteIterator) = bi.getByte
  }

  implicit object TcpPackageOutWriter extends BytesWriter[TcpPackageOut] {
    def write(x: TcpPackageOut, builder: ByteStringBuilder) {
      val (writeMarker, writeMessage) = MarkerByte.writeMessage(x.message)
      writeMarker(builder)

      BytesWriter[Flags].write(Flags(x.userCredentials), builder)
      BytesWriter[Uuid].write(x.correlationId, builder)
      x.userCredentials.foreach(x => BytesWriter[UserCredentials].write(x, builder))

      writeMessage(builder)
    }
  }

  implicit object TcpPackageInReader extends BytesReader[TcpPackageIn] {
    def read(bi: ByteIterator): TcpPackageIn = {
      val readMessage = MarkerByte.readMessage(bi)

      val flags = BytesReader[Flags].read(bi)
      val correlationId = BytesReader[Uuid].read(bi)
      val message = readMessage(bi)

      TcpPackageIn(correlationId, message)
    }
  }
}