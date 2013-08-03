package eventstore
package tcp

import akka.util.ByteString
import com.google.protobuf.{ByteString => PByteString}
import net.sandrogrzicic.scalabuff.MessageBuilder
import scala.reflect.ClassTag
import scala.PartialFunction.condOpt

/**
 * @author Yaroslav Klymko
 */
object Deserializers {
  type Deserialize[T <: In] = ByteString => T
  type Deserializer = ByteString => In
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


  def deserializer[T <: In](implicit deserializer: Deserialize[T]): Deserializer = deserializer

  def empty[T](x: T)(bs: ByteString): T = x

  abstract class DeserializeProto[T <: In, P <: MessageBuilder[P]](implicit ev: ClassTag[P]) extends Deserialize[T] {
    val instance = ev.runtimeClass.getDeclaredMethod("defaultInstance").invoke(ev.runtimeClass).asInstanceOf[P]
    def apply(bs: ByteString) = apply(instance.mergeFrom(bs.toArray[Byte]))
    def apply(x: P): T
  }

  implicit object WriteEventsCompletedDeserializer extends DeserializeProto[AppendToStreamCompleted, proto.WriteEventsCompleted] {
    def apply(x: proto.WriteEventsCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => AppendToStreamFailed(reason, x.`message`)
      case None => AppendToStreamSucceed(x.`firstEventNumber`)
    }
  }

  implicit object TransactionStartCompletedDeserializer extends DeserializeProto[TransactionStartCompleted, proto.TransactionStartCompleted] {
    def apply(x: proto.TransactionStartCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionStartFailed(failed, x.`message`)
      case None => TransactionStartSucceed(x.`transactionId`)
    }
  }

  implicit object TransactionWriteCompletedDeserializer extends DeserializeProto[TransactionWriteCompleted, proto.TransactionWriteCompleted] {
    def apply(x: proto.TransactionWriteCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionWriteFailed(x.`transactionId`, failed, x.`message`)
      case None => TransactionWriteSucceed(x.`transactionId`)
    }
  }

  implicit object TransactionCommitCompletedDeserializer extends DeserializeProto[TransactionCommitCompleted, proto.TransactionCommitCompleted] {
    def apply(x: proto.TransactionCommitCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionCommitFailed(x.`transactionId`, failed, x.`message`)
      case None => TransactionCommitSucceed(x.`transactionId`)
    }
  }

  implicit object DeleteStreamCompletedDeserializer extends DeserializeProto[DeleteStreamCompleted, proto.DeleteStreamCompleted] {
    def apply(x: proto.DeleteStreamCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => DeleteStreamFailed(reason, x.`message`)
      case None => DeleteStreamSucceed
    }
  }

  implicit object ReadEventCompletedDeserializer extends DeserializeProto[ReadEventCompleted, proto.ReadEventCompleted] {

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
      case Some(reason) => ReadEventFailed(reason, x.`error`)
      case None => ReadEventSucceed(resolvedIndexedEvent(x.`event`))
    }
  }

  def readStreamEventsCompleted(direction: ReadDirection.Value)(bs: ByteString) = {
    val x = proto.ReadStreamEventsCompleted.defaultInstance.mergeFrom(bs.toArray[Byte])

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

  def readAllEventsCompleted(direction: ReadDirection.Value)(bs: ByteString) = {
    val x = proto.ReadAllEventsCompleted.defaultInstance.mergeFrom(bs.toArray[Byte])
    ReadAllEventsCompleted(
      position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`),
      events = x.`events`.toList.map(resolvedEvent),
      nextPosition = Position(commitPosition = x.`nextCommitPosition`, preparePosition = x.`nextPreparePosition`),
      direction = direction)
  }


  implicit object subscribeCompletedDeserializer extends DeserializeProto[SubscribeCompleted, proto.SubscriptionConfirmation] {
    def apply(x: proto.SubscriptionConfirmation) = x.`lastEventNumber` match {
      case None => SubscribeToAllCompleted(x.`lastCommitPosition`)
      case Some(eventNumber) => SubscribeToStreamCompleted(x.`lastCommitPosition`, EventNumber(eventNumber))
    }
  }

  implicit object streamEventAppearedDeserialize extends DeserializeProto[StreamEventAppeared, proto.StreamEventAppeared] {
    def apply(x: proto.StreamEventAppeared) = StreamEventAppeared(event = resolvedEvent(x.`event`))
  }

  implicit object subscriptionDroppedDeserialize extends DeserializeProto[SubscriptionDropped, proto.SubscriptionDropped] {
    import proto.SubscriptionDropped.SubscriptionDropReason._

    def reason(x: EnumVal): SubscriptionDropped.Value = x match {
      case Unsubscribed => SubscriptionDropped.Unsubscribed
      case AccessDenied => SubscriptionDropped.AccessDenied
      case _ => SubscriptionDropped.Default
    }

    def apply(x: proto.SubscriptionDropped) =
      SubscriptionDropped(reason = x.`reason`.fold(SubscriptionDropped.Default)(reason))
  }

  def deserialize(markerByte: Byte): Deserializer = markerBytes.get(markerByte).getOrElse(
    sys.error(s"wrong marker byte: ${"0x" + Integer.toHexString(markerByte).drop(6).toUpperCase}"))

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
    position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`))

  def resolvedIndexedEvent(x: proto.ResolvedIndexedEvent) =
    ResolvedIndexedEvent(eventRecord(x.`event`), x.`link`.map(eventRecord))


  val markerBytes: Map[Byte, Deserializer] = Map[Int, Deserializer](
    0x01 -> empty(HeartbeatRequestCommand),
    0x02 -> empty(HeartbeatResponseCommand),
    0x03 -> empty(Ping),
    0x04 -> empty(Pong),

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

    0xB3 -> readStreamEventsCompleted(Forward), // TODO
    0xB5 -> readStreamEventsCompleted(Backward), // TODO

    0xB7 -> readAllEventsCompleted(Forward), // TODO
    0xB9 -> readAllEventsCompleted(Backward), // TODO

    0xC1 -> deserializer[SubscribeCompleted],
    0xC2 -> deserializer[StreamEventAppeared],
    0xC4 -> deserializer[SubscriptionDropped],

    //    ScavengeDatabase = 0xD0,
    0xF0 -> empty(BadRequest)
    //    DeniedToRoute = 0xF1

  ).map {
    case (key, value) => key.toByte -> value
  }


  def byteString(bs: PByteString): ByteString = ByteString(bs.asReadOnlyByteBuffer())

  def byteString(bs: Option[PByteString]): ByteString = bs.fold(ByteString.empty)(x => ByteString(x.asReadOnlyByteBuffer()))

  def uuid(bs: PByteString): Uuid = UuidSerializer.deserialize(byteString(bs))
}
