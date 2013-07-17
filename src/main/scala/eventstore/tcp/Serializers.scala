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
      case HeartbeatResponseCommand => >>(0x02)
      case Ping => >>(0x03)
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
      case x: WriteEvents => writeEvents(0x82, x)

      case x: TransactionStart => transactionStart(0x84, x)
      case x: TransactionWrite => transactionWrite(0x86, x)
      case x: TransactionCommit => transactionCommit(0x88, x)

      case x: DeleteStream => deleteStream(0x8A, x)

      case x: ReadEvent => readEvent(0xB0, x)

      case x: ReadStreamEvents => readStreamEvents(x)

      case x: ReadAllEvents => readAllEvents(x)

      case x: SubscribeToStream => >>(0xC0, proto.SubscribeToStream(x.streamId, x.resolveLinkTos))

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
      `eventStreamId` = x.streamId,
      `fromEventNumber` = x.fromEventNumber,
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def pbyteString(bs: ByteString): PByteString = PByteString.copyFrom(bs.toByteBuffer)

  def byteBuffer(bs: PByteString): ByteString = ByteString(bs.asReadOnlyByteBuffer())

  def newEventX(x: proto.NewEvent) = {
    NewEvent(
      eventId = byteBuffer(x.`eventId`),
      eventType = x.`eventType`,
      isJson = x.`isJson`,
      data = byteBuffer(x.`data`),
      metadata = x.`metadata`.map(byteBuffer))
  }

  def newEvent(x: NewEvent) = {
    proto.NewEvent(
      `eventId` = pbyteString(x.eventId),
      `eventType` = x.eventType,
      `isJson` = x.isJson,
      `data` = pbyteString(x.data),
      `metadata` = x.metadata.map(pbyteString))
  }

  def writeEvents(markerByte: Byte, x: WriteEvents) = {
    >>(markerByte, proto.WriteEvents(
      `eventStreamId` = x.streamId,
      `expectedVersion` = x.expVer.value,
      `events` = x.events.map(newEvent).toVector,
      `requireMaster` = x.requireMaster))
  }


  def readEvent(markerByte: Byte, x: ReadEvent) = {
    >>(markerByte, proto.ReadEvent(
      `eventStreamId` = x.streamId,
      `eventNumber` = x.eventNumber,
      `resolveLinkTos` = x.resolveLinkTos))
  }

  def deleteStream(markerByte: Byte, x: DeleteStream) = {
    >>(markerByte, proto.DeleteStream(
      `eventStreamId` = x.streamId,
      `expectedVersion` = x.expVer.value,
      `requireMaster` = x.requireMaster))
  }


  def transactionStart(markerByte: Byte, x: TransactionStart) = {
    >>(markerByte, proto.TransactionStart(
      `eventStreamId` = x.streamId,
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
}
