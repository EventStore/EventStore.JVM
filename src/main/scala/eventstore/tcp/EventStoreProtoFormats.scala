package eventstore
package tcp

import ReadDirection.{ Backward, Forward }
import proto.{ EventStoreMessages => j, _ }
import util.DefaultFormats
import scala.language.reflectiveCalls
import scala.PartialFunction.condOpt
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.collection.JavaConverters._
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

object EventStoreProtoFormats extends EventStoreProtoFormats

trait EventStoreProtoFormats extends DefaultProtoFormats with DefaultFormats {

  type OperationMessage = Message {
    def getResult(): j.OperationResult
    def hasMessage(): Boolean
    def getMessage(): String
  }

  def range(x: { def getFirstEventNumber(): Int; def getLastEventNumber(): Int }): Option[EventNumber.Range] =
    EventNumber.Range.opt(x.getFirstEventNumber(), x.getLastEventNumber())

  trait ProtoTryReader[T, P <: Message] extends ProtoReader[Try[T], P] {
    import scala.util.Failure

    def failure(reason: Option[EsError], errMsg: Option[String]): Failure[T] =
      failure(EsException(reason getOrElse EsError.Error, message(errMsg)))

    def failure(e: Throwable): Failure[T] = Failure(e)
  }

  trait ProtoOperationReader[T, P <: OperationMessage] extends ProtoTryReader[T, P] {
    def error(x: j.OperationResult): Option[EsError] = {
      import j.OperationResult._
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

    def fromProto(x: P): Try[T] = x.getResult() match {
      case j.OperationResult.Success => Try(success(x))
      case e                         => failure(error(e), option(x.hasMessage(), x.getMessage()))
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

    def fromProto(x: j.EventRecord) = EventRecord(
      streamId = EventStream(x.getEventStreamId),
      number = EventNumber(x.getEventNumber),
      data = EventData(
        eventType = x.getEventType,
        eventId = uuid(x.getEventId),
        data = Content(byteString(x.getData), ContentType(x.getDataContentType)),
        metadata = Content(byteString(x.getMetadata), ContentType(x.getMetadataContentType))) /*,
        created = x.`created` TODO*/ )
  }

  implicit object IndexedEventReader extends ProtoReader[IndexedEvent, j.ResolvedEvent] {

    def parse = j.ResolvedEvent.parseFrom

    def fromProto(x: j.ResolvedEvent) = IndexedEvent(
      event = EventReader.event(x),
      position = Position(commitPosition = x.getCommitPosition, preparePosition = x.getPreparePosition))
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
      option(x.hasLink(), EventRecordReader.fromProto(x.getLink())))
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
    def success(x: j.WriteEventsCompleted) = WriteEventsCompleted(range(x))
  }

  implicit object DeleteStreamWriter extends ProtoWriter[DeleteStream] {
    def toProto(x: DeleteStream) = {
      val builder = j.DeleteStream.newBuilder()
      builder.setEventStreamId(x.streamId.streamId)
      builder.setExpectedVersion(expectedVersion(x.expectedVersion))
      builder.setRequireMaster(x.requireMaster)
      builder.setHardDelete(x.hardDelete)
      builder
    }
  }

  implicit object DeleteStreamCompletedReader
      extends ProtoOperationReader[DeleteStreamCompleted.type, j.DeleteStreamCompleted] {
    def parse = j.DeleteStreamCompleted.parseFrom
    def success(x: j.DeleteStreamCompleted) = DeleteStreamCompleted
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
    def success(x: j.TransactionCommitCompleted) = TransactionCommitCompleted(
      x.getTransactionId,
      numbersRange = range(x))
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
    import j.ReadEventCompleted.ReadEventResult._

    def parse = j.ReadEventCompleted.parseFrom

    def error(x: j.ReadEventCompleted.ReadEventResult): Option[EsError] = condOpt(x) {
      case NotFound      => EsError.EventNotFound
      case NoStream      => EsError.StreamNotFound
      case StreamDeleted => EsError.StreamDeleted
      case Error         => EsError.Error
      case AccessDenied  => EsError.AccessDenied
    }

    def fromProto(x: j.ReadEventCompleted) = x.getResult match {
      case Success => Try(ReadEventCompleted(EventReader.fromProto(x.getEvent)))
      case e       => failure(error(e), option(x.hasError, x.getError))
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
    import j.ReadStreamEventsCompleted.ReadStreamResult._
    import j.ReadStreamEventsCompleted.ReadStreamResult

    def parse = j.ReadStreamEventsCompleted.parseFrom

    def error(x: ReadStreamResult): Option[EsError] = condOpt(x) {
      case NoStream      => EsError.StreamNotFound
      case StreamDeleted => EsError.StreamDeleted
      case Error         => EsError.Error
      case AccessDenied  => EsError.AccessDenied
    }

    def fromProto(x: j.ReadStreamEventsCompleted) = x.getResult match {
      case Success => Try(ReadStreamEventsCompleted(
        events = x.getEventsList.asScala.map(EventReader.fromProto).toList,
        nextEventNumber = EventNumberConverter.to(x.getNextEventNumber),
        lastEventNumber = EventNumber(x.getLastEventNumber),
        endOfStream = x.getIsEndOfStream,
        lastCommitPosition = x.getLastCommitPosition,
        direction = direction))

      case NotModified => failure(new IllegalArgumentException("ReadStreamEventsCompleted.NotModified is not supported"))

      case e           => failure(error(e), option(x.hasError, x.getError))
    }
  }

  object ReadStreamEventsForwardCompletedReader extends ReadStreamEventsCompletedReader(Forward)
  object ReadStreamEventsBackwardCompletedReader extends ReadStreamEventsCompletedReader(Backward)

  implicit object ReadAllEventsWriter extends ProtoWriter[ReadAllEvents] {
    def toProto(x: ReadAllEvents) = {
      val (commitPosition, preparePosition) = PositionConverter.from(x.fromPosition)
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
    import j.ReadAllEventsCompleted.ReadAllResult._
    import j.ReadAllEventsCompleted.ReadAllResult

    def parse = j.ReadAllEventsCompleted.parseFrom

    def error(x: ReadAllResult): Option[EsError] = condOpt(x) {
      case Error        => EsError.Error
      case AccessDenied => EsError.AccessDenied
    }

    def fromProto(x: j.ReadAllEventsCompleted) = (if (x.hasResult) x.getResult else Success) match {
      case Success => Try(ReadAllEventsCompleted(
        position = Position(commitPosition = x.getCommitPosition, preparePosition = x.getPreparePosition),
        events = x.getEventsList.asScala.map(IndexedEventReader.fromProto).toList,
        nextPosition = Position(commitPosition = x.getNextCommitPosition, preparePosition = x.getNextPreparePosition),
        direction = direction))
      case NotModified => failure(new IllegalArgumentException("ReadAllEventsCompleted.NotModified is not supported"))
      case result      => failure(error(result), option(x.hasError, x.getError))
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
        lastEventNumber = if (eventNumber == -1) None else Some(EventNumber(eventNumber)))
    }
  }

  implicit object StreamEventAppearedReader extends ProtoReader[StreamEventAppeared, j.StreamEventAppeared] {
    def parse = j.StreamEventAppeared.parseFrom
    def fromProto(x: j.StreamEventAppeared) = StreamEventAppeared(event = IndexedEventReader.fromProto(x.getEvent))
  }

  implicit object SubscriptionDroppedReader extends ProtoTryReader[UnsubscribeCompleted.type, j.SubscriptionDropped] {
    import j.SubscriptionDropped.SubscriptionDropReason._

    def parse = j.SubscriptionDropped.parseFrom

    def fromProto(x: j.SubscriptionDropped) = {
      (if (x.hasReason) x.getReason else Unsubscribed) match {
        case Unsubscribed => Try(UnsubscribeCompleted)
        case AccessDenied => failure(Some(EsError.AccessDenied), None)
      }
    }
  }

  implicit object ScavengeDatabaseCompletedReader extends ProtoTryReader[ScavengeDatabaseCompleted, j.ScavengeDatabaseCompleted] {

    import j.ScavengeDatabaseCompleted.ScavengeResult._

    def parse = j.ScavengeDatabaseCompleted.parseFrom

    def fromProto(x: j.ScavengeDatabaseCompleted) = x.getResult match {
      case Success => Try(success(x))
      case _       => failure(error(x), option(x.hasError, x.getError))
    }

    def error(x: j.ScavengeDatabaseCompleted): Option[EsError] = condOpt(x.getResult) {
      case InProgress => EsError.ScavengeInProgress(
        totalTimeMs = x.getTotalTimeMs,
        totalSpaceSaved = x.getTotalSpaceSaved)
      case Failed => EsError.ScavengeInProgress(
        totalTimeMs = x.getTotalTimeMs,
        totalSpaceSaved = x.getTotalSpaceSaved)
    }

    def success(x: j.ScavengeDatabaseCompleted) = ScavengeDatabaseCompleted(
      totalTime = FiniteDuration(x.getTotalTimeMs, TimeUnit.MILLISECONDS),
      totalSpaceSaved = x.getTotalSpaceSaved)
  }

  implicit object NotHandledReader extends ProtoReader[EsError.NotHandled, j.NotHandled] {
    import j.NotHandled.NotHandledReason._
    import EsError.NotHandled

    def parse = j.NotHandled.parseFrom

    def masterInfo(x: j.NotHandled.MasterInfo): NotHandled.MasterInfo = NotHandled.MasterInfo(
      tcpAddress = new InetSocketAddress(x.getExternalTcpAddress, x.getExternalTcpPort),
      httpAddress = new InetSocketAddress(x.getExternalHttpAddress, x.getExternalHttpPort),
      tcpSecureAddress = for {
        t <- option(x.hasExternalSecureTcpAddress, x.getExternalSecureTcpAddress)
        p <- option(x.hasExternalSecureTcpPort, x.getExternalSecureTcpPort)
      } yield new InetSocketAddress(t, p))

    def masterInfo(x: Option[j.NotHandled.MasterInfo]): NotHandled.MasterInfo = {
      require(x.isDefined, "additionalInfo is not provided for NotHandled.NotMaster")
      masterInfo(x.get)
    }

    def fromProto(x: j.NotHandled) = NotHandled(x.getReason match {
      case NotReady  => NotHandled.NotReady
      case TooBusy   => NotHandled.TooBusy
      case NotMaster => NotHandled.NotMaster(masterInfo(x.getAdditionalInfo))
      case reason    => throw new IllegalArgumentException(s"NotHandled.$reason is not supported")
    })
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

    def to(x: Int) = Exact.opt(x) getOrElse Last
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
