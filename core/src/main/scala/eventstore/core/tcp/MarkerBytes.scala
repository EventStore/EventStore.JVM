package eventstore
package core
package tcp

import scala.util.{Failure, Try}
import scodec.bits.ByteVector
import eventstore.core.ReadDirection._
import eventstore.core.tcp.EventStoreFormats._
import eventstore.core.{PersistentSubscription => Ps}
import eventstore.core.syntax.Attempt

object MarkerBytes {

  type Reader = BytesReader[Try[In]]

  def readerBy(marker: MarkerByte): Attempt[Reader] =
    readers.get(marker).toRight(s"unknown marker byte: 0x%02X".format(marker))

  private def reader[T <: In](implicit reader: BytesReader[T]): Reader            = reader.map(Try(_))
  private def readerTry[T <: In](implicit r: BytesReader[Try[T]]): Reader         = r
  private def readerFailure(x: ServerError): Reader                               = BytesReader.pure(Failure(x))
  private def readerFailure[T <: ServerError](implicit r: BytesReader[T]): Reader = r.map(Failure(_))

  private val readers: Map[Byte, Reader] = Map[Int, Reader](
    0x01 -> reader[HeartbeatRequest.type],
    0x02 -> reader[HeartbeatResponse.type],
    0x03 -> reader[Ping.type],
    0x04 -> reader[Pong.type],

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

    0x83 -> readerTry[WriteEventsCompleted],
    0x85 -> readerTry[TransactionStartCompleted],
    0x87 -> readerTry[TransactionWriteCompleted],
    0x89 -> readerTry[TransactionCommitCompleted],
    0x8B -> readerTry[DeleteStreamCompleted],
    0xB1 -> readerTry[ReadEventCompleted],

    0xB3 -> readerTry[ReadStreamEventsCompleted](ReadStreamEventsForwardCompletedReader),
    0xB5 -> readerTry[ReadStreamEventsCompleted](ReadStreamEventsBackwardCompletedReader),

    0xB7 -> readerTry[ReadAllEventsCompleted](ReadAllEventsForwardCompletedReader),
    0xB9 -> readerTry[ReadAllEventsCompleted](ReadAllEventsBackwardCompletedReader),

    0xC6 -> reader[Ps.Connected],
    0xC7 -> reader[Ps.EventAppeared],

    0xC9 -> readerTry[Ps.CreateCompleted.type],
    0xCB -> readerTry[Ps.DeleteCompleted.type],
    0xCF -> readerTry[Ps.UpdateCompleted.type],

    0xC1 -> reader[SubscribeCompleted],
    0xC2 -> reader[StreamEventAppeared],
    0xC4 -> readerTry[Unsubscribed.type],

    0xD1 -> readerTry[ScavengeDatabaseResponse],

    0xF0 -> readerFailure(BadRequest),
    0xF1 -> readerFailure[NotHandled],
    0xF3 -> reader[Authenticated.type],
    0xF4 -> readerFailure(NotAuthenticated),

    0xF6 -> reader[ClientIdentified.type]
  ).map {
      case (key, value) => key.toByte -> value
    }

  type Writer = ByteVector

  private def writer[T <: Out](marker: Int, x: T)(implicit writer: BytesWriter[T]): (Writer, Writer) =
    ByteVector(marker) -> writer.write(x)

  def writeMessage(out: Out): (Writer, Writer) = out match {
    case HeartbeatRequest     => writer(0x01, HeartbeatRequest)
    case HeartbeatResponse    => writer(0x02, HeartbeatResponse)
    case Ping                 => writer(0x03, Ping)
    case Pong                 => writer(0x04, Pong)

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

    case x: WriteEvents       => writer(0x82, x)
    case x: TransactionStart  => writer(0x84, x)
    case x: TransactionWrite  => writer(0x86, x)
    case x: TransactionCommit => writer(0x88, x)
    case x: DeleteStream      => writer(0x8A, x)
    case x: ReadEvent         => writer(0xB0, x)
    case x: ReadStreamEvents => x.direction match {
      case Forward  => writer(0xB2, x)
      case Backward => writer(0xB4, x)
    }
    case x: ReadAllEvents => x.direction match {
      case Forward  => writer(0xB6, x)
      case Backward => writer(0xB8, x)
    }

    case x: Ps.Connect     => writer(0xC5, x)
    case x: Ps.Ack         => writer(0xCC, x)
    case x: Ps.Nak         => writer(0xCD, x)
    case x: Ps.Create      => writer(0xC8, x)
    case x: Ps.Delete      => writer(0xCA, x)
    case x: Ps.Update      => writer(0xCE, x)

    case x: SubscribeTo    => writer(0xC0, x)
    case Unsubscribe       => writer(0xC3, Unsubscribe)
    case ScavengeDatabase  => writer(0xD0, ScavengeDatabase)
    case Authenticate      => writer(0xF2, Authenticate)

    case x: IdentifyClient => writer(0xF5, x)
  }
}