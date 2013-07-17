package eventstore
package tcp

import akka.util.ByteString
import eventstore.proto
import com.google.protobuf.{ByteString => PByteString, MessageLite}
import net.sandrogrzicic.scalabuff.MessageBuilder
import scala.reflect.ClassTag

/**
 * @author Yaroslav Klymko
 */
object Deserializers {
  type Deserialize[T <: In] = ByteString => T
  type DeserializeMessage = ByteBuffer => In
  type SerializeMessage = Out => Bytes

  private def operationResult(x: proto.OperationResult.EnumVal) = {
    import proto.OperationResult._
    x match {
      case Success => OperationResult.Success
      case PrepareTimeout => OperationResult.PrepareTimeout
      case CommitTimeout => OperationResult.CommitTimeout
      case ForwardTimeout => OperationResult.ForwardTimeout
      case WrongExpectedVersion => OperationResult.WrongExpectedVersion
      case StreamDeleted => OperationResult.StreamDeleted
      case InvalidTransaction => OperationResult.InvalidTransaction
      case _ => ???
    }
  }

  import ReadDirection.{Backward, Forward}


  def deserializer[T <: In](implicit deserializer: Deserialize[T]): DeserializeMessage = {
    (buffer:ByteBuffer) => deserializer(ByteString(buffer))
  }


  def deserializeX[T](x: T)(buffer: ByteBuffer): T = x

  abstract class DeserializeProto[T <: In, P <: MessageBuilder[P]](implicit ev: ClassTag[P]) extends Deserialize[T] {
    val instance = ev.runtimeClass.getDeclaredMethod("defaultInstance").invoke(ev.runtimeClass).asInstanceOf[P]
    def apply(bs: ByteString) = apply(instance.mergeFrom(bs.toArray[Byte]))
    def apply(x: P): T
  }

  implicit val writeEventsCompletedDeserializer = new DeserializeProto[WriteEventsCompleted, proto.WriteEventsCompleted] {
    def apply(x: proto.WriteEventsCompleted) = WriteEventsCompleted(
      result = operationResult(x.`result`),
      message = x.`message`,
      firstEventNumber = x.`firstEventNumber`)
  }

  implicit val transactionStartCompletedDeserializer = new DeserializeProto[TransactionStartCompleted, proto.TransactionStartCompleted] {
    def apply(x: proto.TransactionStartCompleted) = TransactionStartCompleted(
      transactionId = x.`transactionId`,
      result = operationResult(x.`result`),
      message = x.`message`)
  }

  implicit val transactionWriteCompletedDeserializer = new DeserializeProto[TransactionWriteCompleted, proto.TransactionWriteCompleted] {
    def apply(x: proto.TransactionWriteCompleted) = TransactionWriteCompleted(
      transactionId = x.`transactionId`,
      result = operationResult(x.`result`),
      message = x.`message`)
  }

  implicit val transactionCommitCompletedDeserializer = new DeserializeProto[TransactionCommitCompleted, proto.TransactionCommitCompleted] {
    def apply(x: proto.TransactionCommitCompleted) = TransactionCommitCompleted(
      transactionId = x.`transactionId`,
      result = operationResult(x.`result`),
      message = x.`message`)
  }

  implicit val deleteStreamCompletedDeserializer = new DeserializeProto[DeleteStreamCompleted, proto.DeleteStreamCompleted] {
    import OperationResult._

    /*def apply(x: proto.DeleteStreamCompleted) = operationResult(x.`result`) match {
      case Success => DeleteStreamSucceed
      case result@WrongExpectedVersion => DeleteStreamFailed(x.`message`.getOrElse(
        sys.error(s"DeleteStreamCompleted.message is not given, however operation result: $result")))
      case result => sys.error(s"invalid operation result: $x")
    }*/
    def apply(x: proto.DeleteStreamCompleted) = DeleteStreamCompleted(operationResult(x.`result`), x.`message`)
  }

  implicit val readEventCompletedDeserializer = new DeserializeProto[ReadEventCompleted, proto.ReadEventCompleted] {
    def apply(x: proto.ReadEventCompleted) = ReadEventCompleted(
      result = readEventResult(x.`result`),
      event = resolvedIndexedEvent(x.`event`))
  }




  def readStreamEventsCompleted(direction: ReadDirection.Value)(buffer: ByteBuffer) = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    val x = proto.ReadStreamEventsCompleted.defaultInstance.mergeFrom(bytes)

    def readStreamResult(x: proto.ReadStreamEventsCompleted.ReadStreamResult.EnumVal) = {
      import proto.ReadStreamEventsCompleted.ReadStreamResult._
      x match {
        case Success => ReadStreamResult.Success
        case NoStream => ReadStreamResult.NoStream
        case StreamDeleted => ReadStreamResult.StreamDeleted
        case NotModified => ReadStreamResult.NotModified
        case Error => ReadStreamResult.Error
        case _ => sys.error("TODO")
      }
    }


    ReadStreamEventsCompleted(
      events = x.`events`.map(resolvedIndexedEvent).toList,
      result = readStreamResult(x.`result`),
      nextEventNumber = x.`nextEventNumber`,
      lastEventNumber = x.`lastEventNumber`,
      isEndOfStream = x.`isEndOfStream`,
      lastCommitPosition = x.`lastCommitPosition`,
      direction = direction)
  }


  def readAllEventsCompleted(direction: ReadDirection.Value)(buffer: ByteBuffer) = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    val x = proto.ReadAllEventsCompleted.defaultInstance.mergeFrom(bytes)

    ReadAllEventsCompleted(
      commitPosition = x.`commitPosition`,
      preparePosition = x.`preparePosition`,
      events = x.`events`.toList.map(resolvedEvent),
      nextCommitPosition = x.`nextCommitPosition`,
      nextPreparePosition = x.`nextPreparePosition`,
      direction = direction)
  }


  implicit val subscriptionConfirmationDeserializer = new DeserializeProto[SubscriptionConfirmation, proto.SubscriptionConfirmation] {
    def apply(x: proto.SubscriptionConfirmation) = SubscriptionConfirmation(x.`lastCommitPosition`, x.`lastEventNumber`)
  }

  implicit val streamEventAppearedDeserialize = new DeserializeProto[StreamEventAppeared, proto.StreamEventAppeared] {
    def apply(x: proto.StreamEventAppeared) = StreamEventAppeared(event = resolvedEvent(x.`event`))
  }

  implicit val subscriptionDroppedDeserialize = new DeserializeProto[SubscriptionDropped.type, proto.SubscriptionDropped] {
    def apply(x: proto.SubscriptionDropped) = SubscriptionDropped
  }











  def deserialize(markerByte: Byte): DeserializeMessage = markerBytes.get(markerByte).getOrElse(
    sys.error(s"wrong marker byte: ${"0x" + Integer.toHexString(markerByte).drop(6).toUpperCase}"))






  def noBytes(message: In): DeserializeMessage = _ => message

  def eventRecord(x: proto.EventRecord): EventRecord = EventRecord(
    streamId = x.`eventStreamId`,
    eventNumber = x.`eventNumber`,
    eventId = byteBuffer(x.`eventId`),
    eventType = x.`eventType`,
    data = byteBuffer(x.`data`),
    metadata = x.`metadata`.map(byteBuffer))

  def resolvedEvent(x: proto.ResolvedEvent): ResolvedEvent = ResolvedEvent(
    event = eventRecord(x.`event`),
    link = x.`link`.map(eventRecord),
    commitPosition = x.`commitPosition`,
    preparePosition = x.`preparePosition`)

    def resolvedIndexedEvent(x: proto.ResolvedIndexedEvent) = {
    ResolvedIndexedEvent(eventRecord(x.`event`), x.`link`.map(eventRecord))
  }


  def readEventResult(x: proto.ReadEventCompleted.ReadEventResult.EnumVal): ReadEventResult.Value = {
    import proto.ReadEventCompleted.ReadEventResult._
    x match {
      case Success => ReadEventResult.Success
      case NotFound => ReadEventResult.NotFound
      case NoStream => ReadEventResult.NoStream
      case StreamDeleted => ReadEventResult.StreamDeleted
      case _ => ???
    }
  }

  val markerBytes: Map[Byte, DeserializeMessage] = Map[Int, DeserializeMessage](
    0x01 -> deserializeX(HeartbeatRequestCommand),
    0x04 -> deserializeX(Pong),

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
//    0x81 -> deserializer[CreateStreamCompleted],
    0x83 -> deserializer[WriteEventsCompleted],
    0x85 -> deserializer[TransactionStartCompleted],
    0x87 -> deserializer[TransactionWriteCompleted],
    0x89 -> deserializer[TransactionCommitCompleted],
    0x8B -> deserializer[DeleteStreamCompleted],
    0xB1 -> deserializer[ReadEventCompleted],

    0xB3 -> readStreamEventsCompleted(Forward),
    0xB5 -> readStreamEventsCompleted(Backward),

    0xB7 -> readAllEventsCompleted(Forward),
    0xB9 -> readAllEventsCompleted(Backward),

    0xC1 -> deserializer[SubscriptionConfirmation],
    0xC2 -> deserializer[StreamEventAppeared],
    0xC4 -> deserializer[SubscriptionDropped.type],

    //    ScavengeDatabase = 0xD0,
    0xF0 -> deserializeX(BadRequest)
    //    DeniedToRoute = 0xF1

  ).map {
    case (key, value) => key.toByte -> value
  } // TODO


  def byteBuffer(bs: PByteString): ByteString = ByteString(bs.asReadOnlyByteBuffer())



}
