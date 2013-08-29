package eventstore
package tcp

import scala.PartialFunction.condOpt
import eventstore.util.DefaultFormats
import ReadDirection.{ Backward, Forward }

/**
 * @author Yaroslav Klymko
 */
object EventStoreProtoFormats extends EventStoreProtoFormats

trait EventStoreProtoFormats extends proto.DefaultProtoFormats with DefaultFormats {

  implicit object EventDataWriter extends ProtoWriter[EventData] {
    def toProto(x: EventData) = proto.NewEvent(
      `eventId` = protoByteString(x.eventId),
      `eventType` = x.eventType,
      `dataContentType` = 0,
      `metadataContentType` = 0,
      `data` = protoByteString(x.data),
      `metadata` = protoByteStringOption(x.metadata))
  }

  implicit object EventRecordReader
      extends ProtoReader[EventRecord, proto.EventRecord](proto.EventRecord) {
    def fromProto(x: proto.EventRecord) = EventRecord(
      streamId = EventStream(x.`eventStreamId`),
      number = EventNumber(x.`eventNumber`),
      data = EventData(
        eventId = uuid(x.`eventId`),
        eventType = x.`eventType`,
        data = byteString(x.`data`),
        metadata = byteString(x.`metadata`)))
  }

  implicit object IndexedEventReader extends ProtoReader[IndexedEvent, proto.ResolvedEvent](proto.ResolvedEvent) {
    def fromProto(x: proto.ResolvedEvent) = IndexedEvent(
      event = EventReader.event(x),
      position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`))
  }

  implicit object EventReader extends ProtoReader[Event, proto.ResolvedIndexedEvent](proto.ResolvedIndexedEvent) {
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

  implicit object AppendToStreamWriter extends ProtoWriter[AppendToStream] {
    def toProto(x: AppendToStream) = proto.WriteEvents(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `events` = x.events.map(EventDataWriter.toProto).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit object AppendToStreamCompletedReader
      extends ProtoReader[AppendToStreamCompleted, proto.WriteEventsCompleted](proto.WriteEventsCompleted) {
    def fromProto(x: proto.WriteEventsCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => AppendToStreamFailed(reason, message(x.`message`))
      case None         => AppendToStreamSucceed(EventNumber(x.`firstEventNumber`))
    }
  }

  implicit object DeleteStreamWriter extends ProtoWriter[DeleteStream] {
    def toProto(x: DeleteStream) = proto.DeleteStream(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `requireMaster` = x.requireMaster)
  }

  implicit object DeleteStreamCompletedReader
      extends ProtoReader[DeleteStreamCompleted, proto.DeleteStreamCompleted](proto.DeleteStreamCompleted) {
    def fromProto(x: proto.DeleteStreamCompleted) = operationFailed(x.`result`) match {
      case Some(reason) => DeleteStreamFailed(reason, message(x.`message`))
      case None         => DeleteStreamSucceed
    }
  }

  implicit object TransactionStartWriter extends ProtoWriter[TransactionStart] {
    def toProto(x: TransactionStart) = proto.TransactionStart(
      `eventStreamId` = x.streamId.value,
      `expectedVersion` = expectedVersion(x.expectedVersion),
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionStartCompletedReader
      extends ProtoReader[TransactionStartCompleted, proto.TransactionStartCompleted](proto.TransactionStartCompleted) {
    def fromProto(x: proto.TransactionStartCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionStartFailed(failed, message(x.`message`))
      case None         => TransactionStartSucceed(x.`transactionId`)
    }
  }

  implicit object TransactionWriteWriter extends ProtoWriter[TransactionWrite] {
    def toProto(x: TransactionWrite) = proto.TransactionWrite(
      `transactionId` = x.transactionId,
      `events` = x.events.map(EventDataWriter.toProto).toVector,
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionWriteCompletedReader
      extends ProtoReader[TransactionWriteCompleted, proto.TransactionWriteCompleted](proto.TransactionWriteCompleted) {
    def fromProto(x: proto.TransactionWriteCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionWriteFailed(x.`transactionId`, failed, message(x.`message`))
      case None         => TransactionWriteSucceed(x.`transactionId`)
    }
  }

  implicit object TransactionCommitWriter extends ProtoWriter[TransactionCommit] {
    def toProto(x: TransactionCommit) = proto.TransactionCommit(
      `transactionId` = x.transactionId,
      `requireMaster` = x.requireMaster)
  }

  implicit object TransactionCommitCompletedReader
      extends ProtoReader[TransactionCommitCompleted, proto.TransactionCommitCompleted](proto.TransactionCommitCompleted) {
    def fromProto(x: proto.TransactionCommitCompleted) = operationFailed(x.`result`) match {
      case Some(failed) => TransactionCommitFailed(x.`transactionId`, failed, message(x.`message`))
      case None         => TransactionCommitSucceed(x.`transactionId`)
    }
  }

  implicit object ReadEventWriter extends ProtoWriter[ReadEvent] {
    def toProto(x: ReadEvent) = proto.ReadEvent(
      `eventStreamId` = x.streamId.value,
      `eventNumber` = EventNumberConverter.from(x.eventNumber),
      `resolveLinkTos` = x.resolveLinkTos,
      `requireMaster` = x.requireMaster)
  }

  implicit object ReadEventCompletedReader
      extends ProtoReader[ReadEventCompleted, proto.ReadEventCompleted](proto.ReadEventCompleted) {
    import eventstore.proto.ReadEventCompleted.ReadEventResult._

    // TODO test it, what if new enum will be added in proto?
    def reason(x: EnumVal): Option[ReadEventFailed.Value] = condOpt(x) {
      case NotFound      => ReadEventFailed.NotFound
      case NoStream      => ReadEventFailed.NoStream
      case StreamDeleted => ReadEventFailed.StreamDeleted
      case Error         => ReadEventFailed.Error
      case AccessDenied  => ReadEventFailed.AccessDenied
    }

    def fromProto(x: proto.ReadEventCompleted) = reason(x.`result`) match {
      case Some(reason) => ReadEventFailed(reason, message(x.`error`))
      case None         => ReadEventSucceed(EventReader.fromProto(x.`event`))
    }
  }

  implicit object ReadStreamEventsWriter extends ProtoWriter[ReadStreamEvents] {
    def toProto(x: ReadStreamEvents) = proto.ReadStreamEvents(
      `eventStreamId` = x.streamId.value,
      `fromEventNumber` = EventNumberConverter.from(x.fromEventNumber),
      `maxCount` = x.maxCount,
      `resolveLinkTos` = x.resolveLinkTos,
      `requireMaster` = x.requireMaster)
  }

  abstract class ReadStreamEventsCompletedReader(direction: ReadDirection.Value)
      extends ProtoReader[ReadStreamEventsCompleted, proto.ReadStreamEventsCompleted](proto.ReadStreamEventsCompleted) {
    import eventstore.proto.ReadStreamEventsCompleted.ReadStreamResult._

    // TODO test it, what if new enum will be added in proto?
    def reason(x: EnumVal) = condOpt(x) {
      case NoStream      => ReadStreamEventsFailed.NoStream
      case StreamDeleted => ReadStreamEventsFailed.StreamDeleted
      case Error         => ReadStreamEventsFailed.Error
      case AccessDenied  => ReadStreamEventsFailed.AccessDenied
    }

    def fromProto(x: proto.ReadStreamEventsCompleted) = {
      require(x.`result` != NotModified, "ReadStreamEventsCompleted.NotModified is not supported")
      reason(x.`result`) match {
        case None => ReadStreamEventsSucceed(
          events = x.`events`.map(EventReader.fromProto).toList,
          nextEventNumber = EventNumberConverter.to(x.`nextEventNumber`),
          lastEventNumber = EventNumber(x.`lastEventNumber`),
          endOfStream = x.`isEndOfStream`,
          lastCommitPosition = x.`lastCommitPosition`,
          direction = direction)

        case Some(reason) => ReadStreamEventsFailed(
          reason = reason,
          message = message(x.`error`),
          direction = direction)
      }
    }
  }

  object ReadStreamEventsForwardCompletedReader extends ReadStreamEventsCompletedReader(Forward)
  object ReadStreamEventsBackwardCompletedReader extends ReadStreamEventsCompletedReader(Backward)

  implicit object ReadAllEventsWriter extends ProtoWriter[ReadAllEvents] {
    def toProto(x: ReadAllEvents) = {
      val (commitPosition, preparePosition) = PositionConverter.from(x.position)
      proto.ReadAllEvents(
        `commitPosition` = commitPosition,
        `preparePosition` = preparePosition,
        `maxCount` = x.maxCount,
        `resolveLinkTos` = x.resolveLinkTos,
        `requireMaster` = x.requireMaster)
    }
  }

  abstract class ReadAllEventsCompletedReader(direction: ReadDirection.Value)
      extends ProtoReader[ReadAllEventsCompleted, proto.ReadAllEventsCompleted](proto.ReadAllEventsCompleted) {
    import proto.ReadAllEventsCompleted.ReadAllResult._

    // TODO test it, what if new enum will be added in proto?
    def reason(x: EnumVal): Option[ReadAllEventsFailed.Value] = condOpt(x) {
      case Error        => ReadAllEventsFailed.Error
      case AccessDenied => ReadAllEventsFailed.AccessDenied
    }

    def fromProto(x: proto.ReadAllEventsCompleted) = {
      val result = x.`result` getOrElse Success
      require(result != NotModified, "ReadAllEventsCompleted.NotModified is not supported")
      val position = Position(commitPosition = x.`commitPosition`, preparePosition = x.`preparePosition`)
      reason(result) match {
        case None => ReadAllEventsSucceed(
          position = position,
          events = x.`events`.toList.map(IndexedEventReader.fromProto),
          nextPosition = Position(commitPosition = x.`nextCommitPosition`, preparePosition = x.`nextPreparePosition`),
          direction = direction)

        case Some(reason) => ReadAllEventsFailed(
          reason = reason,
          position = position,
          direction = direction,
          message = message(x.`error`))
      }
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

  implicit object SubscribeCompletedReader
      extends ProtoReader[SubscribeCompleted, proto.SubscriptionConfirmation](proto.SubscriptionConfirmation) {

    def fromProto(x: proto.SubscriptionConfirmation) = x.`lastEventNumber` match {
      case None => SubscribeToAllCompleted(x.`lastCommitPosition`)
      case Some(eventNumber) => SubscribeToStreamCompleted(
        lastCommit = x.`lastCommitPosition`,
        lastEventNumber = if (eventNumber == -1) None else Some(EventNumber(eventNumber)))
    }
  }

  implicit object StreamEventAppearedReader
      extends ProtoReader[StreamEventAppeared, proto.StreamEventAppeared](proto.StreamEventAppeared) {

    def fromProto(x: proto.StreamEventAppeared) =
      StreamEventAppeared(event = IndexedEventReader.fromProto(x.`event`))
  }

  implicit object SubscriptionDroppedReader
      extends ProtoReader[SubscriptionDropped, proto.SubscriptionDropped](proto.SubscriptionDropped) {
    import eventstore.proto.SubscriptionDropped.SubscriptionDropReason._
    val default = SubscriptionDropped.Unsubscribed

    // TODO test it, what if new enum will be added in proto?
    def reason(x: EnumVal): SubscriptionDropped.Value = x match {
      case Unsubscribed => SubscriptionDropped.Unsubscribed
      case AccessDenied => SubscriptionDropped.AccessDenied
      case _            => default
    }

    def fromProto(x: proto.SubscriptionDropped) = SubscriptionDropped(reason = x.`reason`.fold(default)(reason))
  }

  private def operationFailed(x: proto.OperationResult.EnumVal): Option[OperationFailed.Value] = {
    import eventstore.proto.OperationResult._
    // TODO test it, what if new enum will be added in proto?
    condOpt(x) { // TODO add plugin to align this way
      case PrepareTimeout       => OperationFailed.PrepareTimeout
      case CommitTimeout        => OperationFailed.CommitTimeout
      case ForwardTimeout       => OperationFailed.ForwardTimeout
      case WrongExpectedVersion => OperationFailed.WrongExpectedVersion
      case StreamDeleted        => OperationFailed.StreamDeleted
      case InvalidTransaction   => OperationFailed.InvalidTransaction
      case AccessDenied         => OperationFailed.AccessDenied
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
