package eventstore
package tcp


import com.google.protobuf.{ByteString => ProtoByteString, MessageLite}
import ReadDirection._

/**
 * @author Yaroslav Klymko
 */
object Serializers {
  type Serializer[T] = T => ByteString

  def empty(x: Int) = x.toByte -> ByteString.empty

  def serializer[T](markerByte: Int, x: T)(implicit serializer: Serializer[T]) = markerByte.toByte -> serializer(x)

  trait ProtoSerializer[T] extends Serializer[T] {
    def apply(x: T) = ByteString(toProto(x).toByteArray)
    def toProto(x: T): MessageLite

    def newEvent(x: Event) = proto.NewEvent(
      `eventId` = protoByteString(x.eventId),
      `eventType` = x.eventType,
      `dataContentType` = 0,
      `metadataContentType` = 0,
      `data` = protoByteString(x.data),
      `metadata` = protoByteStringOption(x.metadata))

    def protoByteString(bs: ByteString) = ProtoByteString.copyFrom(bs.toByteBuffer)
    def protoByteString(uuid: Uuid) = ProtoByteString.copyFrom(UuidSerializer.serialize(uuid).toByteBuffer)
    def protoByteStringOption(bs: ByteString) = if (bs.isEmpty) None else Some(protoByteString(bs))
  }

  implicit val appendToStreamSerializer: Serializer[AppendToStream] = new ProtoSerializer[AppendToStream] {
    def toProto(x: AppendToStream) = proto.WriteEvents(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `events` = x.events.map(newEvent).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit val transactionStartSerializer: Serializer[TransactionStart] = new ProtoSerializer[TransactionStart] {
    def toProto(x: TransactionStart) = proto.TransactionStart(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `requireMaster` = x.requireMaster)
  }

  implicit val transactionWriteSerializer: Serializer[TransactionWrite] = new ProtoSerializer[TransactionWrite] {
    def toProto(x: TransactionWrite) = proto.TransactionWrite(
      `transactionId` = x.transactionId,
      `events` = x.events.map(newEvent).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit val transactionCommitSerializer: Serializer[TransactionCommit] = new ProtoSerializer[TransactionCommit] {
    def toProto(x: TransactionCommit) = proto.TransactionCommit(
      `transactionId` = x.transactionId,
      `requireMaster` = x.requireMaster)
  }

  implicit val deleteStreamSerializer: Serializer[DeleteStream] = new ProtoSerializer[DeleteStream] {
    def toProto(x: DeleteStream) = proto.DeleteStream(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `requireMaster` = x.requireMaster)
  }

  implicit val readEventSerializer: Serializer[ReadEvent] = new ProtoSerializer[ReadEvent] {
    def toProto(x: ReadEvent) = {
      val eventNumber = x.eventNumber match {
        case EventNumber.Last => -1
        case EventNumber.Exact(x) => x
      }
      proto.ReadEvent(
        `eventStreamId` = x.streamId.value,
        `eventNumber` = eventNumber,
        `resolveLinkTos` = x.resolveLinkTos)
    }
  }

  implicit val readStreamEventsSerializer: Serializer[ReadStreamEvents] = new ProtoSerializer[ReadStreamEvents] {
    def toProto(x: ReadStreamEvents) = proto.ReadStreamEvents(
      `eventStreamId` = x.streamId.value,
      `fromEventNumber` = x.fromEventNumber,
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos)
  }

  implicit val readAllEventsSerializer: Serializer[ReadAllEvents] = new ProtoSerializer[ReadAllEvents] {
    def toProto(x: ReadAllEvents) = proto.ReadAllEvents(
      `commitPosition` = x.position.commitPosition,
      `preparePosition` = x.position.preparePosition,
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos)
  }

  implicit val subscribeToSerializer: Serializer[SubscribeTo] = new ProtoSerializer[SubscribeTo] {
    def toProto(x: SubscribeTo) = {
      val streamId = x.stream match {
        case EventStream.All => ""
        case EventStream.Id(value) => value
      }
      proto.SubscribeToStream(
        `eventStreamId` = streamId,
        `resolveLinkTos` = x.resolveLinkTos)
    }
  }
  
  def serialize(message: Out): (Byte, ByteString) = message match {
    case HeartbeatRequestCommand => empty(0x01)
    case HeartbeatResponseCommand => empty(0x02)
    case Ping => empty(0x03)
    case Pong => empty(0x04)
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
    case x: AppendToStream => serializer(0x82, x)
    case x: TransactionStart => serializer(0x84, x)
    case x: TransactionWrite => serializer(0x86, x)
    case x: TransactionCommit => serializer(0x88, x)
    case x: DeleteStream => serializer(0x8A, x)
    case x: ReadEvent => serializer(0xB0, x)
    case x: ReadStreamEvents => x.direction match {
      case Forward => serializer(0xB2, x)
      case Backward => serializer(0xB4, x)
    }
    case x: ReadAllEvents => x.direction match {
      case Forward => serializer(0xB6, x)
      case Backward => serializer(0xB8, x)
    }
    case x: SubscribeTo => serializer(0xC0, x)
    case UnsubscribeFromStream => empty(0xC3)
    case ScavengeDatabase => empty(0xD0)
  }
}
