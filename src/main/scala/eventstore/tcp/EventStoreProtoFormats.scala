package eventstore
package tcp

import ReadDirection.{ Backward, Forward }
import eventstore.proto.OperationResult
import eventstore.util.DefaultFormats
import scala.PartialFunction.condOpt
import scala.language.reflectiveCalls
import scala.util.Try

object EventStoreProtoFormats extends EventStoreProtoFormats

trait EventStoreProtoFormats extends proto.DefaultProtoFormats with DefaultFormats {

  type OperationMessage[T] = Message[T] {
    def `result`: OperationResult.EnumVal
    def `message`: Option[String]
  }

  trait ProtoTryReader[T, P <: Message[P]] extends ProtoReader[Try[T], P] {
    import scala.util.Failure

    def failure(reason: Option[EsError], errMsg: Option[String]): Failure[T] =
      failure(EsException(reason getOrElse EsError.Error, message(errMsg)))

    def failure(e: Throwable): Failure[T] = Failure(e)
  }

  trait ProtoOperationReader[T, P <: OperationMessage[P]] extends ProtoTryReader[T, P] {

    import scala.util.Success

    def error(x: proto.OperationResult.EnumVal): Option[EsError] = {
      import eventstore.proto.OperationResult._
      // TODO test it, what if new enum will be added in proto?
      condOpt(x) {
        case PrepareTimeout       => EsError.PrepareTimeout
        case CommitTimeout        => EsError.CommitTimeout
        case ForwardTimeout       => EsError.ForwardTimeout
        case WrongExpectedVersion => EsError.WrongExpectedVersion
        case StreamDeleted        => EsError.StreamDeleted
        case InvalidTransaction   => EsError.InvalidTransaction
        case AccessDenied         => EsError.AccessDenied
      }
    }

    def fromProto(x: P): Try[T] = x.`result` match {
      case OperationResult.Success => Success(success(x))
      case _                       => failure(error(x.`result`), x.`message`)
    }

    def success(x: P): T
  }

  implicit object EventDataWriter extends ProtoWriter[EventData] {
    def toProto(x: EventData) = proto.NewEvent(
      `eventId` = protoByteString(x.eventId),
      `eventType` = x.eventType,
      `dataContentType` = x.data.contentType.value,
      `data` = protoByteString(x.data.value),
      `metadataContentType` = x.metadata.contentType.value,
      `metadata` = protoByteStringOption(x.metadata.value))
  }

  implicit object EventRecordReader extends ProtoReader[EventRecord, proto.EventRecord] {

    def provider = proto.EventRecord

    def fromProto(x: proto.EventRecord) = EventRecord(
      streamId = EventStream(x.`eventStreamId`),
      number = EventNumber(x.`eventNumber`),
      data = EventData(
        eventType = x.`eventType`,
        eventId = uuid(x.`eventId`),
        data = Content(byteString(x.`data`), ContentType(x.`dataContentType`)),
        metadata = Content(byteString(x.`metadata`), ContentType(x.`metadataContentType`))))
  }

  implicit object IndexedEventReader extends ProtoReader[IndexedEvent, proto.ResolvedEvent] {

    def provider = proto.ResolvedEvent

    def fromProto(x: proto.ResolvedEvent) = IndexedEvent(
      event = EventReader.event(x),
      position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`))
  }

  implicit object EventReader extends ProtoReader[Event, proto.ResolvedIndexedEvent] {

    def provider = proto.ResolvedIndexedEvent

    def fromProto(x: proto.ResolvedIndexedEvent) = event(
      EventRecordReader.fromProto(x.`event`),
      x.`link`.map(EventRecordReader.fromProto))

    def event(event: EventRecord, linkEvent: Option[EventRecord]): Event = linkEvent match {
      case Some(x) => ResolvedEvent(linkedEvent = event, linkEvent = x)
      case None    => event
    }

    def event(x: { def `event`: proto.EventRecord; def `link`: Option[proto.EventRecord] }): Event = event(
      EventRecordReader.fromProto(x.`event`),
      x.`link`.map(EventRecordReader.fromProto))
  }

  implicit object WriteEventsWriter extends ProtoWriter[WriteEvents] {
    def toProto(x: WriteEvents) = proto.WriteEvents(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `events` = x.events.map(EventDataWriter.toProto).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit object WriteEventsCompletedReader extends ProtoOperationReader[WriteEventsCompleted, proto.WriteEventsCompleted] {
    def provider = proto.WriteEventsCompleted
    def success(x: proto.WriteEventsCompleted) = WriteEventsCompleted(EventNumber(x.`firstEventNumber`))
  }

  implicit object DeleteStreamWriter extends ProtoWriter[DeleteStream] {
    def toProto(x: DeleteStream) = proto.DeleteStream(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `requireMaster` = x.requireMaster)
  }

  implicit object DeleteStreamCompletedReader
      extends ProtoOperationReader[DeleteStreamCompleted.type, proto.DeleteStreamCompleted] {
    def provider = proto.DeleteStreamCompleted
    def success(x: proto.DeleteStreamCompleted) = DeleteStreamCompleted
  }

  implicit object TransactionStartWriter extends ProtoWriter[TransactionStart] {
    def toProto(x: TransactionStart) = proto.TransactionStart(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionStartCompletedReader
      extends ProtoOperationReader[TransactionStartCompleted, proto.TransactionStartCompleted] {
    def provider = proto.TransactionStartCompleted
    def success(x: proto.TransactionStartCompleted) = TransactionStartCompleted(x.`transactionId`)
  }

  implicit object TransactionWriteWriter extends ProtoWriter[TransactionWrite] {
    def toProto(x: TransactionWrite) = proto.TransactionWrite(
      `transactionId` = x.transactionId,
      `events` = x.events.map(EventDataWriter.toProto).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionWriteCompletedReader
      extends ProtoOperationReader[TransactionWriteCompleted, proto.TransactionWriteCompleted] {
    def provider = proto.TransactionWriteCompleted
    def success(x: proto.TransactionWriteCompleted) = TransactionWriteCompleted(x.`transactionId`)
  }

  implicit object TransactionCommitWriter extends ProtoWriter[TransactionCommit] {
    def toProto(x: TransactionCommit) = proto.TransactionCommit(
      `transactionId` = x.transactionId,
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionCommitCompletedReader
      extends ProtoOperationReader[TransactionCommitCompleted, proto.TransactionCommitCompleted] {
    def provider = proto.TransactionCommitCompleted
    def success(x: proto.TransactionCommitCompleted) = TransactionCommitCompleted(x.`transactionId`)
  }

  implicit object ReadEventWriter extends ProtoWriter[ReadEvent] {
    def toProto(x: ReadEvent) = proto.ReadEvent(
      `eventStreamId` = x.streamId.value,
      `eventNumber` = EventNumberConverter.from(x.eventNumber),
      `resolveLinkTos` = x.resolveLinkTos,
      `requireMaster` = x.requireMaster)
  }

  implicit object ReadEventCompletedReader extends ProtoTryReader[ReadEventCompleted, proto.ReadEventCompleted] {
    import proto.ReadEventCompleted.ReadEventResult._

    def provider = proto.ReadEventCompleted

    // TODO test it, what if new enum will be added in proto?
    def error(x: EnumVal): Option[EsError] = condOpt(x) {
      case NotFound      => EsError.EventNotFound
      case NoStream      => EsError.StreamNotFound
      case StreamDeleted => EsError.StreamDeleted
      case Error         => EsError.Error
      case AccessDenied  => EsError.AccessDenied
    }

    def fromProto(x: proto.ReadEventCompleted) = x.`result` match {
      case Success => Try(ReadEventCompleted(EventReader.fromProto(x.`event`)))
      case _       => failure(error(x.`result`), x.`error`)
    }
  }

  implicit object ReadStreamEventsWriter extends ProtoWriter[ReadStreamEvents] {
    def toProto(x: ReadStreamEvents) = proto.ReadStreamEvents(
      `eventStreamId` = x.streamId.value,
      `fromEventNumber` = EventNumberConverter.from(x.fromNumber),
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos,
      `requireMaster` = x.requireMaster)
  }

  abstract class ReadStreamEventsCompletedReader(direction: ReadDirection)
      extends ProtoTryReader[ReadStreamEventsCompleted, proto.ReadStreamEventsCompleted] {

    import eventstore.proto.ReadStreamEventsCompleted.ReadStreamResult._

    def provider = proto.ReadStreamEventsCompleted

    // TODO test it, what if new enum will be added in proto?
    def error(x: EnumVal): Option[EsError] = condOpt(x) {
      case NoStream      => EsError.StreamNotFound
      case StreamDeleted => EsError.StreamDeleted
      case Error         => EsError.Error
      case AccessDenied  => EsError.AccessDenied
    }

    def fromProto(x: proto.ReadStreamEventsCompleted) = x.`result` match {
      case Success => Try(ReadStreamEventsCompleted(
        events = x.`events`.map(EventReader.fromProto).toList,
        nextEventNumber = EventNumberConverter.to(x.`nextEventNumber`),
        lastEventNumber = EventNumber(x.`lastEventNumber`),
        endOfStream = x.`isEndOfStream`,
        lastCommitPosition = x.`lastCommitPosition`,
        direction = direction))

      case NotModified => failure(new IllegalArgumentException("ReadStreamEventsCompleted.NotModified is not supported"))

      case _           => failure(error(x.`result`), x.`error`)
    }
  }

  object ReadStreamEventsForwardCompletedReader extends ReadStreamEventsCompletedReader(Forward)
  object ReadStreamEventsBackwardCompletedReader extends ReadStreamEventsCompletedReader(Backward)

  implicit object ReadAllEventsWriter extends ProtoWriter[ReadAllEvents] {
    def toProto(x: ReadAllEvents) = {
      val (commitPosition, preparePosition) = PositionConverter.from(x.fromPosition)
      proto.ReadAllEvents(
        `commitPosition` = commitPosition,
        `preparePosition` = preparePosition,
        `maxCount` = x.maxCount,
        `resolveLinkTos` = x.resolveLinkTos,
        `requireMaster` = x.requireMaster)
    }
  }

  abstract class ReadAllEventsCompletedReader(direction: ReadDirection)
      extends ProtoTryReader[ReadAllEventsCompleted, proto.ReadAllEventsCompleted] {
    import proto.ReadAllEventsCompleted.ReadAllResult._

    def provider = proto.ReadAllEventsCompleted

    // TODO test it, what if new enum will be added in proto?
    def error(x: EnumVal): Option[EsError] = condOpt(x) {
      case Error        => EsError.Error
      case AccessDenied => EsError.AccessDenied
    }

    def fromProto(x: proto.ReadAllEventsCompleted) = x.`result` getOrElse Success match {
      case Success => Try(ReadAllEventsCompleted(
        position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`),
        events = x.`events`.toList.map(IndexedEventReader.fromProto),
        nextPosition = Position(commitPosition = x.`nextCommitPosition`, preparePosition = x.`nextPreparePosition`),
        direction = direction))
      case NotModified => failure(new IllegalArgumentException("ReadAllEventsCompleted.NotModified is not supported"))
      case result      => failure(error(result), x.`error`)
    }
  }

  object ReadAllEventsForwardCompletedReader extends ReadAllEventsCompletedReader(Forward)
  object ReadAllEventsBackwardCompletedReader extends ReadAllEventsCompletedReader(Backward)

  implicit object SubscribeToWriter extends ProtoWriter[SubscribeTo] {
    def toProto(x: SubscribeTo) = {
      val streamId = x.stream match {
        case EventStream.All    => ""
        case EventStream.Id(id) => id
      }
      proto.SubscribeToStream(
        `eventStreamId` = streamId,
        `resolveLinkTos` = x.resolveLinkTos)
    }
  }

  implicit object SubscribeCompletedReader extends ProtoReader[SubscribeCompleted, proto.SubscriptionConfirmation] {

    def provider = proto.SubscriptionConfirmation

    def fromProto(x: proto.SubscriptionConfirmation) = x.`lastEventNumber` match {
      case None => SubscribeToAllCompleted(x.`lastCommitPosition`)
      case Some(eventNumber) => SubscribeToStreamCompleted(
        lastCommit = x.`lastCommitPosition`,
        lastEventNumber = if (eventNumber == -1) None else Some(EventNumber(eventNumber)))
    }
  }

  implicit object StreamEventAppearedReader extends ProtoReader[StreamEventAppeared, proto.StreamEventAppeared] {

    def provider = proto.StreamEventAppeared

    def fromProto(x: proto.StreamEventAppeared) =
      StreamEventAppeared(event = IndexedEventReader.fromProto(x.`event`))
  }

  implicit object SubscriptionDroppedReader extends ProtoTryReader[UnsubscribeCompleted.type, proto.SubscriptionDropped] {

    import eventstore.proto.SubscriptionDropped.SubscriptionDropReason._

    def provider = proto.SubscriptionDropped

    def fromProto(x: proto.SubscriptionDropped) = x.`reason` match {
      case None | Some(Unsubscribed) => Try(UnsubscribeCompleted)
      case Some(AccessDenied)        => failure(Some(EsError.AccessDenied), None)
      case _                         => failure(None, None)
    }
  }

  private def expectedVersion(x: ExpectedVersion): Int = {
    import ExpectedVersion._
    x match {
      case NoStream => -1
      case Any      => -2
      case Exact(v) => v
    }
  }

  trait Converter[A, B] {
    def from(x: A): B
    def to(x: B): A
  }

  private object EventNumberConverter extends Converter[EventNumber, Int] {
    import EventNumber._

    def from(x: EventNumber): Int = x match {
      case Exact(value) => value
      case Last         => -1
    }

    def to(x: Int) = if (x == -1) Last else EventNumber(x)
  }

  private object PositionConverter extends Converter[Position, (Long, Long)] {
    import Position._

    def from(x: Position) = x match {
      case Last        => (-1, -1)
      case Exact(c, p) => (c, p)
    }

    def to(x: (Long, Long)) = x match {
      case (-1, -1) => Last
      case (c, p)   => Exact(commitPosition = c, preparePosition = p)
    }
  }
}
