package eventstore
package tcp

import ReadDirection.{ Backward, Forward }
import org.joda.time.DateTime
import proto.{ EventStoreMessages => j, _ }
import eventstore.util.{ ToCoarsest, DefaultFormats }
import scala.language.reflectiveCalls
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit

object EventStoreProtoFormats extends EventStoreProtoFormats

trait EventStoreProtoFormats extends DefaultProtoFormats with DefaultFormats {

  type OperationMessage = Message {
    def getResult(): j.OperationResult
    def hasMessage(): Boolean
    def getMessage(): String
  }

  type HasRange = {
    def getFirstEventNumber(): Int
    def getLastEventNumber(): Int
  }

  private def range(x: HasRange): Option[EventNumber.Range] =
    EventNumber.Range.opt(x.getFirstEventNumber(), x.getLastEventNumber())

  type HasPosition = {
    def getCommitPosition(): Long
    def getPreparePosition(): Long
  }

  private def position(x: HasPosition): Position.Exact = {
    Position.Exact(commitPosition = x.getCommitPosition(), preparePosition = x.getPreparePosition())
  }

  type HasPositionOpt = HasPosition {
    def hasCommitPosition(): Boolean
    def hasPreparePosition(): Boolean
  }

  private def positionOpt(x: HasPositionOpt): Option[Position.Exact] = {
    if (x.hasCommitPosition() &&
      x.hasPreparePosition() &&
      x.getCommitPosition() >= 0 &&
      x.getPreparePosition() >= 0) Some(position(x))
    else None
  }

  trait ProtoTryReader[T, P <: Message] extends ProtoReader[Try[T], P] {
    import scala.util.Failure

    def failure(e: Throwable): Failure[T] = Failure(e)
  }

  trait ProtoOperationReader[T, P <: OperationMessage] extends ProtoTryReader[T, P] {
    def fromProto(x: P): Try[T] = {
      import j.OperationResult._
      import eventstore.{ OperationError => E }

      x.getResult() match {
        case Success              => Try(success(x))
        case PrepareTimeout       => failure(E.PrepareTimeout)
        case CommitTimeout        => failure(E.CommitTimeout)
        case ForwardTimeout       => failure(E.ForwardTimeout)
        case WrongExpectedVersion => failure(E.WrongExpectedVersion)
        case StreamDeleted        => failure(E.StreamDeleted)
        case InvalidTransaction   => failure(E.InvalidTransaction)
        case AccessDenied         => failure(E.AccessDenied)
      }
    }

    def success(x: P): T
  }

  implicit object EventDataWriter extends ProtoWriter[EventData] {
    def toProto(x: EventData) = {
      val builder = j.NewEvent.newBuilder()
      builder.setEventId(protoByteString(x.eventId))
      builder.setEventType(x.eventType)
      builder.setDataContentType(x.data.contentType.value)
      builder.setData(protoByteString(x.data.value))
      builder.setMetadataContentType(x.metadata.contentType.value)
      protoByteStringOption(x.metadata.value).foreach(builder.setMetadata)
      builder
    }
  }

  implicit object EventRecordReader extends ProtoReader[EventRecord, j.EventRecord] {

    def parse = j.EventRecord.parseFrom

    def fromProto(x: j.EventRecord) = {
      val streamId = x.getEventStreamId

      if (streamId == "") EventRecord.Deleted
      else EventRecord(
        streamId = EventStream.Id(x.getEventStreamId),
        number = EventNumber.Exact(x.getEventNumber),
        data = EventData(
          eventType = x.getEventType,
          eventId = uuid(x.getEventId),
          data = Content(byteString(x.getData), ContentType(x.getDataContentType)),
          metadata = Content(byteString(x.getMetadata), ContentType(x.getMetadataContentType))
        ),
        created = option(x.hasCreatedEpoch, new DateTime(x.getCreatedEpoch))
      )
    }
  }

  implicit object IndexedEventReader extends ProtoReader[IndexedEvent, j.ResolvedEvent] {

    def parse = j.ResolvedEvent.parseFrom

    def fromProto(x: j.ResolvedEvent) = IndexedEvent(
      event = EventReader.event(x),
      position = position(x)
    )
  }

  implicit object EventReader extends ProtoReader[Event, j.ResolvedIndexedEvent] {
    type JEvent = {
      def getEvent(): j.EventRecord
      def hasLink(): Boolean
      def getLink(): j.EventRecord
    }

    def parse = j.ResolvedIndexedEvent.parseFrom

    def fromProto(x: j.ResolvedIndexedEvent) = event(x)

    def event(event: EventRecord, linkEvent: Option[EventRecord]): Event = linkEvent match {
      case Some(x) => ResolvedEvent(linkedEvent = event, linkEvent = x)
      case None    => event
    }

    def event(x: JEvent): Event = event(
      EventRecordReader.fromProto(x.getEvent()),
      option(x.hasLink(), EventRecordReader.fromProto(x.getLink()))
    )
  }

  implicit object WriteEventsWriter extends ProtoWriter[WriteEvents] {
    def toProto(x: WriteEvents) = {
      val builder = j.WriteEvents.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setExpectedVersion(expectedVersion(x.expectedVersion))
      builder.addAllEvents(x.events.map(EventDataWriter.toProto(_).build()).toIterable.asJava)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  implicit object WriteEventsCompletedReader extends ProtoOperationReader[WriteEventsCompleted, j.WriteEventsCompleted] {
    def parse = j.WriteEventsCompleted.parseFrom
    def success(x: j.WriteEventsCompleted) = WriteEventsCompleted(range(x), positionOpt(x))
  }

  implicit object DeleteStreamWriter extends ProtoWriter[DeleteStream] {
    def toProto(x: DeleteStream) = {
      val builder = j.DeleteStream.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setExpectedVersion(expectedVersion(x.expectedVersion))
      builder.setRequireMaster(x.requireMaster)
      builder.setHardDelete(x.hard)
      builder
    }
  }

  implicit object DeleteStreamCompletedReader
      extends ProtoOperationReader[DeleteStreamCompleted, j.DeleteStreamCompleted] {
    def parse = j.DeleteStreamCompleted.parseFrom
    def success(x: j.DeleteStreamCompleted) = DeleteStreamCompleted(positionOpt(x))
  }

  implicit object TransactionStartWriter extends ProtoWriter[TransactionStart] {
    def toProto(x: TransactionStart) = {
      val builder = j.TransactionStart.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setExpectedVersion(expectedVersion(x.expectedVersion))
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  implicit object TransactionStartCompletedReader
      extends ProtoOperationReader[TransactionStartCompleted, j.TransactionStartCompleted] {
    def parse = j.TransactionStartCompleted.parseFrom
    def success(x: j.TransactionStartCompleted) = TransactionStartCompleted(x.getTransactionId)
  }

  implicit object TransactionWriteWriter extends ProtoWriter[TransactionWrite] {
    def toProto(x: TransactionWrite) = {
      val builder = j.TransactionWrite.newBuilder()
      builder.setTransactionId(x.transactionId)
      builder.addAllEvents(x.events.map(EventDataWriter.toProto(_).build()).toIterable.asJava)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  implicit object TransactionWriteCompletedReader
      extends ProtoOperationReader[TransactionWriteCompleted, j.TransactionWriteCompleted] {
    def parse = j.TransactionWriteCompleted.parseFrom
    def success(x: j.TransactionWriteCompleted) = TransactionWriteCompleted(x.getTransactionId)
  }

  implicit object TransactionCommitWriter extends ProtoWriter[TransactionCommit] {
    def toProto(x: TransactionCommit) = {
      val builder = j.TransactionCommit.newBuilder()
      builder.setTransactionId(x.transactionId)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  implicit object TransactionCommitCompletedReader
      extends ProtoOperationReader[TransactionCommitCompleted, j.TransactionCommitCompleted] {
    def parse = j.TransactionCommitCompleted.parseFrom
    def success(x: j.TransactionCommitCompleted) = {
      TransactionCommitCompleted(
        transactionId = x.getTransactionId,
        numbersRange = range(x),
        position = positionOpt(x)
      )
    }
  }

  implicit object ReadEventWriter extends ProtoWriter[ReadEvent] {
    def toProto(x: ReadEvent) = {
      val builder = j.ReadEvent.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setEventNumber(EventNumberConverter.from(x.eventNumber))
      builder.setResolveLinkTos(x.resolveLinkTos)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  implicit object ReadEventCompletedReader extends ProtoTryReader[ReadEventCompleted, j.ReadEventCompleted] {
    def parse = j.ReadEventCompleted.parseFrom

    def fromProto(x: j.ReadEventCompleted) = {
      import j.ReadEventCompleted.ReadEventResult._
      import eventstore.{ ReadEventError => E }

      def failure(x: ReadEventError) = this.failure(x)
      x.getResult match {
        case Success       => Try(ReadEventCompleted(EventReader.fromProto(x.getEvent)))
        case NotFound      => failure(E.EventNotFound)
        case NoStream      => failure(E.StreamNotFound)
        case StreamDeleted => failure(E.StreamDeleted)
        case Error         => failure(E.Error(message(option(x.hasError, x.getError))))
        case AccessDenied  => failure(E.AccessDenied)
      }
    }
  }

  implicit object ReadStreamEventsWriter extends ProtoWriter[ReadStreamEvents] {
    def toProto(x: ReadStreamEvents) = {
      val builder = j.ReadStreamEvents.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setFromEventNumber(EventNumberConverter.from(x.fromNumber))
      builder.setMaxCount(x.maxCount)
      builder.setResolveLinkTos(x.resolveLinkTos)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  abstract class ReadStreamEventsCompletedReader(direction: ReadDirection)
      extends ProtoTryReader[ReadStreamEventsCompleted, j.ReadStreamEventsCompleted] {

    def parse = j.ReadStreamEventsCompleted.parseFrom

    def fromProto(x: j.ReadStreamEventsCompleted) = {
      import j.ReadStreamEventsCompleted.ReadStreamResult._
      import eventstore.{ ReadStreamEventsError => E }

      def failure(x: ReadStreamEventsError) = this.failure(x)

      def readStreamEventsCompleted = ReadStreamEventsCompleted(
        events = x.getEventsList.asScala.map(EventReader.fromProto).toList,
        nextEventNumber = EventNumber(x.getNextEventNumber),
        lastEventNumber = EventNumber.Exact(x.getLastEventNumber),
        endOfStream = x.getIsEndOfStream,
        lastCommitPosition = x.getLastCommitPosition,
        direction = direction
      )

      x.getResult match {
        case Success       => Try(readStreamEventsCompleted)
        case NoStream      => failure(E.StreamNotFound)
        case StreamDeleted => failure(E.StreamDeleted)
        case NotModified   => this.failure(new IllegalArgumentException("ReadStreamEventsCompleted.NotModified is not supported"))
        case Error         => failure(E.Error(message(option(x.hasError, x.getError))))
        case AccessDenied  => failure(E.AccessDenied)
      }
    }
  }

  object ReadStreamEventsForwardCompletedReader extends ReadStreamEventsCompletedReader(Forward)
  object ReadStreamEventsBackwardCompletedReader extends ReadStreamEventsCompletedReader(Backward)

  implicit object ReadAllEventsWriter extends ProtoWriter[ReadAllEvents] {
    def toProto(x: ReadAllEvents) = {
      val (commitPosition, preparePosition) = x.fromPosition match {
        case Position.Last        => (-1L, -1L)
        case Position.Exact(c, p) => (c, p)
      }
      val builder = j.ReadAllEvents.newBuilder()
      builder.setCommitPosition(commitPosition)
      builder.setPreparePosition(preparePosition)
      builder.setMaxCount(x.maxCount)
      builder.setResolveLinkTos(x.resolveLinkTos)
      builder.setRequireMaster(x.requireMaster)
      builder
    }
  }

  abstract class ReadAllEventsCompletedReader(direction: ReadDirection)
      extends ProtoTryReader[ReadAllEventsCompleted, j.ReadAllEventsCompleted] {

    def parse = j.ReadAllEventsCompleted.parseFrom

    def fromProto(x: j.ReadAllEventsCompleted) = {
      import j.ReadAllEventsCompleted.ReadAllResult._
      import eventstore.{ ReadAllEventsError => E }

      def failure(x: ReadAllEventsError) = this.failure(x)

      def readAllEventsCompleted = ReadAllEventsCompleted(
        position = position(x),
        events = x.getEventsList.asScala.map(IndexedEventReader.fromProto).toList,
        nextPosition = Position.Exact(commitPosition = x.getNextCommitPosition, preparePosition = x.getNextPreparePosition),
        direction = direction
      )

      val result = if (x.hasResult) x.getResult else Success

      result match {
        case Success      => Try(readAllEventsCompleted)
        case NotModified  => this.failure(new IllegalArgumentException("ReadAllEventsCompleted.NotModified is not supported"))
        case Error        => failure(E.Error(message(option(x.hasError, x.getError))))
        case AccessDenied => failure(E.AccessDenied)
      }
    }
  }

  object ReadAllEventsForwardCompletedReader extends ReadAllEventsCompletedReader(Forward)
  object ReadAllEventsBackwardCompletedReader extends ReadAllEventsCompletedReader(Backward)

  implicit object SubscribeToWriter extends ProtoWriter[SubscribeTo] {
    def toProto(x: SubscribeTo) = {
      val streamId = x.stream match {
        case EventStream.All    => ""
        case id: EventStream.Id => id.streamId
      }
      val builder = j.SubscribeToStream.newBuilder()
      builder.setEventStreamId(streamId)
      builder.setResolveLinkTos(x.resolveLinkTos)
      builder
    }
  }

  implicit object SubscribeCompletedReader extends ProtoReader[SubscribeCompleted, j.SubscriptionConfirmation] {

    def parse = j.SubscriptionConfirmation.parseFrom

    def fromProto(x: j.SubscriptionConfirmation) = option(x.hasLastEventNumber, x.getLastEventNumber) match {
      case None => SubscribeToAllCompleted(x.getLastCommitPosition)
      case Some(eventNumber) => SubscribeToStreamCompleted(
        lastCommit = x.getLastCommitPosition,
        lastEventNumber = EventNumber.Exact.opt(eventNumber)
      )
    }
  }

  implicit object StreamEventAppearedReader extends ProtoReader[StreamEventAppeared, j.StreamEventAppeared] {
    def parse = j.StreamEventAppeared.parseFrom
    def fromProto(x: j.StreamEventAppeared) = StreamEventAppeared(event = IndexedEventReader.fromProto(x.getEvent))
  }

  implicit object SubscriptionDroppedReader extends ProtoTryReader[Unsubscribed.type, j.SubscriptionDropped] {

    def parse = j.SubscriptionDropped.parseFrom

    def fromProto(x: j.SubscriptionDropped) = {
      import j.SubscriptionDropped.{ SubscriptionDropReason => P }

      def unsubscribed = Try(Unsubscribed)
      if (!x.hasReason) unsubscribed
      else x.getReason match {
        case P.Unsubscribed => unsubscribed
        case P.AccessDenied => failure(SubscriptionDropped.AccessDenied)
      }
    }
  }

  implicit object ScavengeDatabaseCompletedReader extends ProtoTryReader[ScavengeDatabaseCompleted, j.ScavengeDatabaseCompleted] {

    import j.ScavengeDatabaseCompleted.ScavengeResult._

    def parse = j.ScavengeDatabaseCompleted.parseFrom

    def fromProto(x: j.ScavengeDatabaseCompleted) = {
      import eventstore.{ ScavengeError => E }

      def scavengeDatabaseCompleted = ScavengeDatabaseCompleted(
        totalTime = ToCoarsest(FiniteDuration(x.getTotalTimeMs, TimeUnit.MILLISECONDS)),
        totalSpaceSaved = x.getTotalSpaceSaved
      )

      def failure(x: ScavengeError) = this.failure(x)

      // TODO test this
      x.getResult match {
        case Success    => Try(scavengeDatabaseCompleted)
        case InProgress => failure(E.InProgress)
        case Failed     => failure(E.Failed(message(option(x.hasError, x.getError))))
      }
    }
  }

  implicit object NotHandledReader extends ProtoReader[NotHandled, j.NotHandled] {
    import j.NotHandled.NotHandledReason._

    def parse = j.NotHandled.parseFrom

    def masterInfo(x: j.NotHandled.MasterInfo): NotHandled.MasterInfo = NotHandled.MasterInfo(
      tcpAddress = x.getExternalTcpAddress :: x.getExternalTcpPort,
      httpAddress = x.getExternalHttpAddress :: x.getExternalHttpPort,
      tcpSecureAddress = for {
      h <- option(x.hasExternalSecureTcpAddress, x.getExternalSecureTcpAddress)
      p <- option(x.hasExternalSecureTcpPort, x.getExternalSecureTcpPort)
    } yield h :: p
    )

    def masterInfo(x: Option[j.NotHandled.MasterInfo]): NotHandled.MasterInfo = {
      require(x.isDefined, "additionalInfo is not provided for NotHandled.NotMaster")
      masterInfo(x.get)
    }

    def fromProto(x: j.NotHandled) = {
      val reason = x.getReason match {
        case NotReady  => NotHandled.NotReady
        case TooBusy   => NotHandled.TooBusy
        case NotMaster => NotHandled.NotMaster(masterInfo(x.getAdditionalInfo))
      }
      NotHandled(reason)
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

    def to(x: Int) = EventNumber(x)
  }
}
