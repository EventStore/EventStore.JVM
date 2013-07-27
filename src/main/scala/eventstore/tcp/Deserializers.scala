package eventstore
package tcp

import akka.util.ByteString
import eventstore.proto
import com.google.protobuf.{ByteString => PByteString}
import net.sandrogrzicic.scalabuff.MessageBuilder
import scala.reflect.ClassTag
import PartialFunction.condOpt

/**
 * @author Yaroslav Klymko
 */
object Deserializers {
  type Deserialize[T <: In] = ByteString => T
  type DeserializeMessage = ByteBuffer => In
  type SerializeMessage = Out => Bytes

  private def operationFailed(x: proto.OperationResult.EnumVal): Option[OperationFailed.Value] = {
    import proto.OperationResult._
    condOpt(x) {
      case PrepareTimeout => OperationFailed.PrepareTimeout
      case CommitTimeout => OperationFailed.CommitTimeout
      case ForwardTimeout => OperationFailed.ForwardTimeout
      case WrongExpectedVersion => OperationFailed.WrongExpectedVersion
      case StreamDeleted => OperationFailed.StreamDeleted
      case InvalidTransaction => OperationFailed.InvalidTransaction
      case AccessDenied => OperationFailed.AccessDenied
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

  implicit val writeEventsCompletedDeserializer = new DeserializeProto[AppendToStreamCompleted, proto.WriteEventsCompleted] {
    def apply(x: proto.WriteEventsCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => AppendToStreamFailed(reason, x.`message` getOrElse sys.error(s"AppendToStreamCompleted.result is not given, however operation result is $reason"))
      case None => AppendToStreamSucceed(x.`firstEventNumber`)
    }
  }

  implicit val transactionStartCompletedDeserializer = new DeserializeProto[TransactionStartCompleted, proto.TransactionStartCompleted] {
    def apply(x: proto.TransactionStartCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionStartFailed(failed, x.`message`)
      case None => TransactionStartSucceed(x.`transactionId`)
    }
  }

  implicit val transactionWriteCompletedDeserializer = new DeserializeProto[TransactionWriteCompleted, proto.TransactionWriteCompleted] {
    def apply(x: proto.TransactionWriteCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionWriteFailed(x.`transactionId`, failed, x.`message`)
      case None => TransactionWriteSucceed(x.`transactionId`)
    }
  }

  implicit val transactionCommitCompletedDeserializer = new DeserializeProto[TransactionCommitCompleted, proto.TransactionCommitCompleted] {
    def apply(x: proto.TransactionCommitCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionCommitFailed(x.`transactionId`, failed, x.`message`)
      case None => TransactionCommitSucceed(x.`transactionId`)
    }
  }

  implicit val deleteStreamCompletedDeserializer = new DeserializeProto[DeleteStreamCompleted, proto.DeleteStreamCompleted] {
    def apply(x: proto.DeleteStreamCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => DeleteStreamFailed(reason, x.`message` getOrElse sys.error(s"DeleteStreamCompleted.message is not given, however operation result is $reason"))
      case None => DeleteStreamSucceed
    }
  }

  implicit val readEventCompletedDeserializer = new DeserializeProto[ReadEventCompleted, proto.ReadEventCompleted] {

    def readEventFailed(x: proto.ReadEventCompleted.ReadEventResult.EnumVal): Option[ReadEventFailed.Value] = {
      import proto.ReadEventCompleted.ReadEventResult._

      condOpt(x) {
        case NotFound => ReadEventFailed.NotFound
        case NoStream => ReadEventFailed.NoStream
        case StreamDeleted => ReadEventFailed.StreamDeleted
        case Error => ReadEventFailed.Error
        case AccessDenied => ReadEventFailed.AccessDenied
      }
    }

    def apply(x: proto.ReadEventCompleted) = readEventFailed(x.`result`) match {
      //TODO why empty message case Some(reason) => ReadEventFailed(reason, x.`error` getOrElse sys.error(s"ReadEventCompleted.error is not given, however operation result is $reason"))
      case Some(reason) => ReadEventFailed(reason, x.`error` getOrElse "")
      case None => ReadEventSucceed(resolvedIndexedEvent(x.`event`))
    }
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
      nextEventNumber = /*EventNumber*/(x.`nextEventNumber`),
      lastEventNumber = /*EventNumber*/(x.`lastEventNumber`),
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


  implicit val subscribeCompletedDeserializer = new DeserializeProto[SubscribeCompleted, proto.SubscriptionConfirmation] {
    def apply(x: proto.SubscriptionConfirmation) = x.`lastEventNumber` match {
      case None => SubscribeToAllCompleted(x.`lastCommitPosition`)
      case Some(eventNumber) => SubscribeToStreamCompleted(x.`lastCommitPosition`, EventNumber(eventNumber))
    }
  }

  implicit val streamEventAppearedDeserialize = new DeserializeProto[StreamEventAppeared, proto.StreamEventAppeared] {
    def apply(x: proto.StreamEventAppeared) = StreamEventAppeared(event = resolvedEvent(x.`event`))
  }

  implicit val subscriptionDroppedDeserialize = new DeserializeProto[SubscriptionDropped, proto.SubscriptionDropped] {
    import proto.SubscriptionDropped.SubscriptionDropReason._

    def reason(x: EnumVal): SubscriptionDropped.Value = x match {
      case Unsubscribed => SubscriptionDropped.Unsubscribed
      case AccessDenied => SubscriptionDropped.AccessDenied
      case _ => SubscriptionDropped.Default
    }

    def apply(x: proto.SubscriptionDropped) =
      SubscriptionDropped(reason = x.`reason`.fold(SubscriptionDropped.Default)(reason))
  }



  def deserialize(markerByte: Byte): DeserializeMessage = markerBytes.get(markerByte).getOrElse(
    sys.error(s"wrong marker byte: ${"0x" + Integer.toHexString(markerByte).drop(6).toUpperCase}"))

  def noBytes(message: In): DeserializeMessage = _ => message

  def eventRecord(x: proto.EventRecord): EventRecord = EventRecord(
    streamId = EventStream.Id(x.`eventStreamId`),
    number = EventNumber.Exact(x.`eventNumber`),
    event = Event(
      eventId = uuid(x.`eventId`),
      eventType = x.`eventType`,
      data = byteString(x.`data`),
      metadata = byteString(x.`metadata`)))

  def resolvedEvent(x: proto.ResolvedEvent): ResolvedEvent = ResolvedEvent(
    event = eventRecord(x.`event`),
    link = x.`link`.map(eventRecord),
    commitPosition = x.`commitPosition`,
    preparePosition = x.`preparePosition`)

  def resolvedIndexedEvent(x: proto.ResolvedIndexedEvent) =
    ResolvedIndexedEvent(eventRecord(x.`event`), x.`link`.map(eventRecord))


  val markerBytes: Map[Byte, DeserializeMessage] = Map[Int, DeserializeMessage](
    0x01 -> deserializeX(HeartbeatRequestCommand),
    0x02 -> deserializeX(HeartbeatResponseCommand),
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
    0x83 -> deserializer[AppendToStreamCompleted],
    0x85 -> deserializer[TransactionStartCompleted],
    0x87 -> deserializer[TransactionWriteCompleted],
    0x89 -> deserializer[TransactionCommitCompleted],
    0x8B -> deserializer[DeleteStreamCompleted],
    0xB1 -> deserializer[ReadEventCompleted],

    0xB3 -> readStreamEventsCompleted(Forward),
    0xB5 -> readStreamEventsCompleted(Backward),

    0xB7 -> readAllEventsCompleted(Forward),
    0xB9 -> readAllEventsCompleted(Backward),

    0xC1 -> deserializer[SubscribeCompleted],
    0xC2 -> deserializer[StreamEventAppeared],
    0xC4 -> deserializer[SubscriptionDropped],

    //    ScavengeDatabase = 0xD0,
    0xF0 -> deserializeX(BadRequest)
    //    DeniedToRoute = 0xF1

  ).map {
    case (key, value) => key.toByte -> value
  } // TODO


  def byteString(bs: PByteString): ByteString = ByteString(bs.asReadOnlyByteBuffer())

  def byteString(bs: Option[PByteString]): ByteString = bs.fold(ByteString.empty)(x => ByteString(x.asReadOnlyByteBuffer()))

  def uuid(bs: PByteString): Uuid = UuidSerializer.deserialize(byteString(bs))
}
