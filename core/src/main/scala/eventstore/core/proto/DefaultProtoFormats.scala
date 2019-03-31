package eventstore
package core
package proto

import scodec.bits.ByteVector
import com.google.protobuf.Message.Builder
import com.google.protobuf.{ByteString => PBS}
import eventstore.core.util.DefaultFormats
import eventstore.core.syntax._

object DefaultProtoFormats extends DefaultProtoFormats

trait DefaultProtoFormats extends DefaultFormats {

  type Message = com.google.protobuf.Message

  trait ProtoReader[T, P <: Message] extends BytesReader[T] {

    def parse: Array[Byte] => P
    def fromProto(x: P): T

    final def read(bi: ByteVector)                     = Right(ReadResult(fromProto(parse(bi.toArray))))
    final def byteString(bs: PBS): ByteString          = ByteString(bs.asReadOnlyByteBuffer())
    final def byteString(bs: Option[PBS]): ByteString  = bs.fold(ByteString.empty)(x => ByteString(x.asReadOnlyByteBuffer()))
    final def uuidUnsafe(bs: PBS): Uuid                = BytesReader[Uuid].read(ByteVector(bs.toByteArray)).unsafe.value

    final def message(x: Option[String]): Option[String] = x match {
      case Some("")                  => None
      case Some(s) if s.trim.isEmpty => None
      case _                         => x
    }
  }

  trait ProtoWriter[T] extends BytesWriter[T] {

    def toProto(x: T): Builder

    final def write(x: T): ByteVector                            = ByteVector(toProto(x).build().toByteArray)
    final def protoByteString(bs: ByteString)                    = PBS.copyFrom(bs.toByteBuffer)
    final def protoByteString(uuid: Uuid)                        = PBS.copyFrom(BytesWriter[Uuid].write(uuid).toByteBuffer)
    final def protoByteStringOption(bs: ByteString): Option[PBS] = if (!bs.nonEmpty) None else Some(protoByteString(bs))
  }

  def option[T](b: Boolean, f: => T): Option[T] = if (b) Some(f) else None
}