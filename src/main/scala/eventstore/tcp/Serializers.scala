package eventstore
package tcp


import com.google.protobuf.{ByteString => PByteString, MessageLite}

/**
 * @author Yaroslav Klymko
 */
object Serializers {

  implicit def intToByte(x: Int): Byte = x.toByte
  def serialize(message: Out): (Byte, Bytes) = {
    message match {
      case HeartbeatRequestCommand => >>(0x01)
      case HeartbeatResponseCommand => >>(0x02)
      case Ping => >>(0x03)
      case Pong => >>(0x04)
      //
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
      //

      case x: AppendToStream => appendToStream(0x82, x)

      case x: TransactionStart => transactionStart(0x84, x)
      case x: TransactionWrite => transactionWrite(0x86, x)
      case x: TransactionCommit => transactionCommit(0x88, x)

      case x: DeleteStream => deleteStream(0x8A, x)

      case x: ReadEvent => readEvent(0xB0, x)

      case x: ReadStreamEvents => readStreamEvents(x)

      case x: ReadAllEvents => readAllEvents(x)

      case x: SubscribeTo => subscribeTo(0xC0, x)

      case UnsubscribeFromStream => >>(0xC3, proto.UnsubscribeFromStream.defaultInstance)

      case ScavengeDatabase => >>(0xD0)

      case _ => sys.error(s"$message is not supported")
    }
  }


  val empty = Array.empty[Byte]

  def >>(markerByte: Byte): (Byte, Bytes) = markerByte -> empty

  def >>(markerByte: Byte, message: MessageLite): (Byte, Bytes) = markerByte -> message.toByteArray


  def readAllEvents(x: ReadAllEvents) = {
    val markerByte: Byte = x.direction match {
      case ReadDirection.Forward => 0xB6
      case ReadDirection.Backward => 0xB8
    }

    >>(markerByte, proto.ReadAllEvents(
      `commitPosition` = x.commitPosition,
      `preparePosition` = x.preparePosition,
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def readStreamEvents(x: ReadStreamEvents) = {
    val markerByte: Byte = x.direction match {
      case ReadDirection.Forward => 0xB2
      case ReadDirection.Backward => 0xB4
    }
    >>(markerByte, proto.ReadStreamEvents(
      `eventStreamId` = x.streamId.value,
      `fromEventNumber` = x.fromEventNumber,
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def pbyteString(bs: ByteString): PByteString = PByteString.copyFrom(bs.toByteBuffer)

  def pbyteString(uuid: Uuid): PByteString = PByteString.copyFrom(UuidSerializer.serialize(uuid).toByteBuffer) // TODO PERF

  def pbyteStringOption(bs: ByteString): Option[PByteString] = if (bs.isEmpty) None else Some(pbyteString(bs))


  def subscribeTo(markerByte: Byte, x: SubscribeTo) = {
    val streamId = x.stream match {
      case EventStream.All => ""
      case EventStream.Id(value) => value
    }

    >>(markerByte, proto.SubscribeToStream(
      `eventStreamId` = streamId,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def newEvent(x: Event) = {
    proto.NewEvent(
      `eventId` = pbyteString(x.eventId),
      `eventType` = x.eventType,
      `dataContentType` = 0,
      `metadataContentType` = 0,
      `data` = pbyteString(x.data),
      `metadata` = pbyteStringOption(x.metadata))
  }

  def appendToStream(markerByte: Byte, x: AppendToStream) = {
    >>(markerByte, proto.WriteEvents(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `events` = x.events.map(newEvent).toVector,
      `requireMaster` = x.requireMaster))
  }


  def readEvent(markerByte: Byte, x: ReadEvent) = {
    >>(markerByte, proto.ReadEvent(
      `eventStreamId` = x.streamId.value,
      `eventNumber` = x.eventNumber.proto,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def deleteStream(markerByte: Byte, x: DeleteStream) = {
    >>(markerByte, proto.DeleteStream(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `requireMaster` = x.requireMaster))
  }


  def transactionStart(markerByte: Byte, x: TransactionStart) = {
    >>(markerByte, proto.TransactionStart(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = x.expVer.value,
      `requireMaster` = x.requireMaster))
  }

  def transactionWrite(markerByte: Byte, x: TransactionWrite) = {
    >>(markerByte, proto.TransactionWrite(
      `transactionId` = x.transactionId,
      `events` = x.events.map(newEvent).toVector,
      `requireMaster` = x.requireMaster))
  }

  def transactionCommit(markerByte: Byte, x: TransactionCommit) = {
    >>(markerByte, proto.TransactionCommit(
      `transactionId` = x.transactionId,
      `requireMaster` = x.requireMaster))
  }


  def uuid(bs: ByteString): Uuid = UuidSerializer.deserialize(bs)

  implicit class RichEventNumber(val self: EventNumber) extends AnyVal {
    def proto: Int = self match {
      case EventNumber.Last => -1
      case EventNumber.Exact(x) => x
    }
  }
}
