package eventstore
package proto

import akka.util.{ ByteStringBuilder, ByteIterator }
import com.google.protobuf.{ ByteString => ProtoByteString }
import com.google.protobuf.Message.Builder
import scala.language.reflectiveCalls
import util.{ BytesWriter, BytesReader, DefaultFormats }

object DefaultProtoFormats extends DefaultProtoFormats

trait DefaultProtoFormats extends DefaultFormats {

  type Message = com.google.protobuf.Message

  trait ProtoReader[T, P <: Message] extends BytesReader[T] {

    def parse: (java.io.InputStream => P)

    def fromProto(x: P): T

    def read(bi: ByteIterator): T = fromProto(parse(bi.asInputStream))

    def byteString(bs: ProtoByteString): ByteString = ByteString(bs.asReadOnlyByteBuffer())

    def byteString(bs: Option[ProtoByteString]): ByteString = bs.fold(ByteString.empty)(x => ByteString(x.asReadOnlyByteBuffer()))

    def uuid(bs: ProtoByteString): Uuid = BytesReader[Uuid].read(byteString(bs))

    def message(x: Option[String]): Option[String] = x match {
      case Some("")                  => None
      case Some(s) if s.trim.isEmpty => None
      case _                         => x
    }
  }

  trait ProtoWriter[T] extends BytesWriter[T] {

    def toProto(x: T): Builder

    def write(x: T, builder: ByteStringBuilder) {
      builder.putBytes(toProto(x).build().toByteArray)
    }

    def protoByteString(bs: ByteString) = ProtoByteString.copyFrom(bs.toByteBuffer)

    def protoByteString(uuid: Uuid) = ProtoByteString.copyFrom(BytesWriter[Uuid].toByteString(uuid).toByteBuffer)

    def protoByteStringOption(bs: ByteString) = if (bs.isEmpty) None else Some(protoByteString(bs))
  }

  def option[T](b: Boolean, f: => T): Option[T] = if (b) Some(f) else None
}