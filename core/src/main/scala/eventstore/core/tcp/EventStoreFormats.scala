package eventstore
package core
package tcp

import scodec.bits.ByteVector
import eventstore.core.util._
import eventstore.core.syntax._
import IntToByteVector.uint8

object EventStoreFormats extends EventStoreFormats

trait EventStoreFormats extends EventStoreProtoFormats {

  abstract class EmptyFormat[T](obj: T) extends BytesFormat[T] {
    def read(bv: ByteVector)    = Right(ReadResult(obj))
    def write(x: T): ByteVector = ByteVector.empty
  }

  implicit object HeartbeatRequestFormat extends EmptyFormat(HeartbeatRequest)
  implicit object HeartbeatResponseFormat extends EmptyFormat(HeartbeatResponse)
  implicit object PingFormat extends EmptyFormat(Ping)
  implicit object PongFormat extends EmptyFormat(Pong)
  implicit object ClientIdentifiedFormat extends EmptyFormat(ClientIdentified)
  implicit object UnsubscribeFromStreamFormat extends EmptyFormat(Unsubscribe)
  implicit object ScavengeDatabaseFormat extends EmptyFormat(ScavengeDatabase)
  implicit object AuthenticateFormat extends EmptyFormat(Authenticate)
  implicit object AuthenticatedFormat extends EmptyFormat(Authenticated)

  implicit object UserCredentialsFormat extends BytesFormat[UserCredentials] {

    def write(uc: UserCredentials): ByteVector = {

      def putString(s: String, name: String): ByteVector = {
        val bvs = ByteVector.encodeUtf8(s).orError
        require(bvs.size < 256, s"$name serialized length should be less than 256 bytes, but is ${bvs.size}:$uc")
        val size = uint8(bvs.size.toInt).unsafe
        size ++ bvs
      }

      putString(uc.login, "login") ++ putString(uc.password, "password")

    }

    def read(bv: ByteVector): Attempt[ReadResult[UserCredentials]] = {

      val getString: BytesReader[String] = bv => {
        val length = bv.take(1).toInt(signed = false)
        bv.tail.consume(length.toLong)(_.decodeUtf8.leftMap(_.toString)).map {
          case (rem, a) => ReadResult(a, rem)
        }
      }

      val reader = for {
           login <- getString
        password <- getString
      } yield UserCredentials(login, password)

      reader.read(bv)
    }

  }

  implicit object ByteFormat extends BytesFormat[Byte] {
    def write(b: Byte): ByteVector                      = ByteVector(b)
    def read(bv: ByteVector): Attempt[ReadResult[Byte]] = bv.headOption match {
      case Some(h) => Right(ReadResult(h, bv.tail))
      case None    => Left("ByteVector empty")
    }
  }

  implicit val PackOutWriter: BytesWriter[PackOut] = (x: PackOut) => {

    val (marker, message) = MarkerBytes.writeMessage(x.message)

    val flags       = BytesWriter[Flags].write(Flags(x.credentials))
    val correlation = BytesWriter[Uuid].write(x.correlationId)
    val credentials = x.credentials.fold(ByteVector.empty)(BytesWriter[UserCredentials].write)

    marker ++ flags ++ correlation ++ credentials ++ message
  }

  implicit val PackInReader: BytesReader[PackIn] = for {
         mb <- BytesReader[MarkerByte]
    readMsg <- BytesReader.lift(MarkerBytes.readerBy(mb))
    _       <- BytesReader[Flags]
    corr    <- BytesReader[Uuid]
    msg     <- readMsg
  } yield PackIn(msg, corr)

}