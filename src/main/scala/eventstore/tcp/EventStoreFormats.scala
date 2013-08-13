package eventstore
package tcp

import util.{BytesWriter, BytesReader, BytesFormat}
import akka.util.{ByteStringBuilder, ByteIterator}
import ReadDirection._

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

  implicit object AuthDataFormat extends BytesFormat[AuthData] {
    def write(x: AuthData, builder: ByteStringBuilder) {
      def putString(s: String) {
        val bs = ByteString(s)
        builder.putByte(bs.size.toByte)
        builder.append(bs)
      }
      putString(x.login)
      putString(x.password)
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
      AuthData(login, password)
    }
  }

  object MarkerByte {
    type Reader = ByteIterator => In

    def readMessage(bi: ByteIterator): (ByteIterator => In) = {
      val markerByte = bi.getByte
      readers.get(markerByte).getOrElse(sys.error(s"unknown marker byte: ${"0x%02X".format(markerByte)}"))
    }

    def reader[T <: In](implicit reader: BytesReader[T]): Reader = reader.read

    val readers: Map[Byte, Reader] = Map[Int, Reader](
      0x01 -> reader[HeartbeatRequestCommand.type],
      0x02 -> reader[HeartbeatResponseCommand.type],
      0x03 -> reader[Ping.type], // TODO ADD TEST
      0x04 -> reader[Pong.type], // TODO ADD TEST

      //    PrepareAck = 0x05,
      //    CommitAck = 0x06,
      //
      //    SlaveAssignment = 0x07,
      //    CloneAssignment = 0x08,
      //
      //    SubscribeReplica = 0x10,
      //    CreateChunk = 0x11,
      //    PhysicalChunkBulk = 0x12,
      //    LogicalChunkBulk = 0x13,
      //
      0x83 -> reader[AppendToStreamCompleted],
      0x85 -> reader[TransactionStartCompleted],
      0x87 -> reader[TransactionWriteCompleted],
      0x89 -> reader[TransactionCommitCompleted],
      0x8B -> reader[DeleteStreamCompleted],
      0xB1 -> reader[ReadEventCompleted],

      0xB3 -> reader[ReadStreamEventsCompleted](ReadStreamEventsForwardCompletedReader),
      0xB5 -> reader[ReadStreamEventsCompleted](ReadStreamEventsBackwardCompletedReader),

      0xB7 -> reader[ReadAllEventsCompleted](ReadAllEventsForwardCompletedReader),
      0xB9 -> reader[ReadAllEventsCompleted](ReadAllEventsBackwardCompletedReader),

      0xC1 -> reader[SubscribeCompleted],
      0xC2 -> reader[StreamEventAppeared],
      0xC4 -> reader[SubscriptionDropped],

      //    ScavengeDatabase = 0xD0,
      0xF0 -> reader[BadRequest.type] // TODO add test
      //    DeniedToRoute = 0xF1
    ).map {
      case (key, value) => key.toByte -> value
    }

    type Writer = ByteStringBuilder => Unit

    def writer[T <: Out](markerByte: Int, x: T)(implicit writer: BytesWriter[T]): (Writer, Writer) = {
      def writeByte(bb: ByteStringBuilder) {
        bb.putByte(markerByte.toByte)
      }
      def writeMessage(bb: ByteStringBuilder) {
        writer.write(x, bb)
      }
      (writeByte _) -> (writeMessage _)
    }

    def writeMessage(out: Out): (Writer, Writer) = out match {
      case x@HeartbeatRequestCommand => writer[HeartbeatRequestCommand.type](0x01, x)
      case x@HeartbeatResponseCommand => writer[HeartbeatResponseCommand.type](0x02, x)
      case x@Ping => writer[Ping.type](0x03, x)
      case x@Pong => writer[Pong.type](0x04, x)
      //    PrepareAck = 0x05,
      //    CommitAck = 0x06,
      //
      //    SlaveAssignment = 0x07,
      //    CloneAssignment = 0x08,
      //
      //    SubscribeReplica = 0x10,
      //    CreateChunk = 0x11,
      //    PhysicalChunkBulk = 0x12,
      //    LogicalChunkBulk = 0x 13,
      case x: AppendToStream => writer(0x82, x)
      case x: TransactionStart => writer(0x84, x)
      case x: TransactionWrite => writer(0x86, x)
      case x: TransactionCommit => writer(0x88, x)
      case x: DeleteStream => writer(0x8A, x)
      case x: ReadEvent => writer(0xB0, x)
      case x: ReadStreamEvents => x.direction match {
        case Forward => writer(0xB2, x)
        case Backward => writer(0xB4, x)
      }
      case x: ReadAllEvents => x.direction match {
        case Forward => writer(0xB6, x)
        case Backward => writer(0xB8, x)
      }
      case x: SubscribeTo => writer(0xC0, x)
      case x@UnsubscribeFromStream => writer(0xC3, x)
      case x@ScavengeDatabase => writer(0xD0, x) // TODO
    }
  }

  implicit object TcpPackageOutWriter extends BytesWriter[TcpPackageOut] {
    def write(x: TcpPackageOut, builder: ByteStringBuilder) {
      val (writeMarker, writeMessage) = MarkerByte.writeMessage(x.message)
      writeMarker(builder)

      val flags = x.auth.fold[Set[Flag.Value]](Set())(_ => Set(Flag.Auth))
      Flag.write(flags, builder)

      BytesWriter.write(x.correlationId, builder)

      x.auth.foreach(x => BytesWriter.write(x, builder))
      writeMessage(builder)
    }
  }

  implicit object TcpPackageInReader extends BytesReader[TcpPackageIn] {
    def read(bi: ByteIterator): TcpPackageIn = {
      val readMessage = MarkerByte.readMessage(bi)

      val flags = Flag.deserialize(bi)
      val correlationId = BytesReader.read[Uuid](bi)

      val authData = if (flags contains Flag.Auth) Some(BytesReader.read[AuthData](bi)) else None

      val message = readMessage(bi)
      TcpPackageIn(correlationId, message, authData)
    }
  }
}