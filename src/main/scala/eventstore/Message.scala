package eventstore

import util.BetterToString

/**
 * @author Yaroslav Klymko
 */

sealed trait Message
sealed trait In extends Message
sealed trait Out extends Message
sealed trait InOut extends In with Out

case object HeartbeatRequestCommand extends InOut
case object HeartbeatResponseCommand extends InOut

case object Ping extends Out
case object Pong extends In

case object PrepareAck extends Message
case object CommitAck extends Message

case object SlaveAssignment extends Message
case object CloneAssignment extends Message

case object SubscribeReplica extends Message
case object CreateChunk extends Message
case object PhysicalChunkBulk extends Message
case object LogicalChunkBulk extends Message

case class Event(eventId: Uuid,
                 eventType: String,
//                 dataContentType: Int,
                 data: ByteString,
//                 metadataContentType: Int,
                 metadata: ByteString) extends BetterToString

object Event {

  def apply(eventId: Uuid, eventType: String): Event = Event(eventId, eventType, ByteString.empty, ByteString.empty)

  object StreamDeleted {
    def unapply(x: Event): Option[Uuid] = PartialFunction.condOpt(x) {
      case Event(eventId, EvenType.streamDeleted, ByteString.empty, ByteString.empty) => eventId
    }
  }
}

object EvenType {
  val streamDeleted = "$streamDeleted"
  val streamCreated = "$streamCreated"
}

case class EventRecord(streamId: EventStream.Id, number: EventNumber.Exact, event: Event)

case class ResolvedIndexedEvent(eventRecord: EventRecord, link: Option[EventRecord])

case class ResolvedEvent(event: EventRecord,
                         link: Option[EventRecord],
                         commitPosition: Long,
                         preparePosition: Long)


case class DeniedToRoute(externalTcpAddress: String,
                         externalTcpPort: Int,
                         externalHttpAddress: String,
                         externalHttpPort: Int) extends Message



case class AppendToStream(streamId: EventStream.Id,
                          expVer: ExpectedVersion,
                          events: List[Event],
                          requireMaster: Boolean) extends Out

object AppendToStream {
  def apply(streamId: EventStream.Id, expVer: ExpectedVersion, events: List[Event]): AppendToStream = AppendToStream(
    streamId = streamId,
    expVer = expVer,
    events = events,
    requireMaster = true)
}

sealed trait AppendToStreamCompleted extends In
case class AppendToStreamSucceed(firstEventNumber: Int) extends AppendToStreamCompleted
case class AppendToStreamFailed(reason: OperationFailed.Value, message: Option[String]) extends AppendToStreamCompleted



case class DeleteStream(streamId: EventStream.Id,
                        expVer: ExpectedVersion, // TODO disallow NoVersion
                        requireMaster: Boolean) extends Out

sealed trait DeleteStreamCompleted extends In
case object DeleteStreamSucceed extends DeleteStreamCompleted
case class DeleteStreamFailed(result: OperationFailed.Value, message: Option[String]) extends DeleteStreamCompleted



case class ReadEvent(streamId: EventStream.Id, eventNumber: EventNumber, resolveLinkTos: Boolean) extends Out

sealed trait ReadEventCompleted extends In
case class ReadEventSucceed(event: ResolvedIndexedEvent) extends ReadEventCompleted
case class ReadEventFailed(reason: ReadEventFailed.Value, message: Option[String]) extends ReadEventCompleted
object ReadEventFailed extends Enumeration {
  val NotFound, NoStream, StreamDeleted, Error, AccessDenied = Value
}



case class ReadStreamEvents(streamId: EventStream.Id,
                            fromEventNumber: Int,
                            maxCount: Int,
                            resolveLinkTos: Boolean,
                            direction: ReadDirection.Value) extends Out

// TODO simplify
case class ReadStreamEventsCompleted(events: List[ResolvedIndexedEvent],
                                     result: ReadStreamResult.Value,
                                     nextEventNumber: Int,
                                     lastEventNumber: Int,
                                     isEndOfStream: Boolean,
                                     lastCommitPosition: Long,
                                     direction: ReadDirection.Value) extends In

object ReadStreamResult extends Enumeration {
  val Success, NoStream, StreamDeleted, NotModified, Error, AccessDenied = Value
}



case class ReadAllEvents(commitPosition: Long,
                         preparePosition: Long,
                         maxCount: Int,
                         resolveLinkTos: Boolean,
                         direction: ReadDirection.Value) extends Out

case class ReadAllEventsCompleted(commitPosition: Long,
                                  preparePosition: Long,
                                  events: List[ResolvedEvent],
                                  nextCommitPosition: Long,
                                  nextPreparePosition: Long,
                                  direction: ReadDirection.Value) extends In



object ReadDirection extends Enumeration {
  val Forward, Backward = Value
}


case class TransactionStart(streamId: EventStream.Id, expVer: ExpectedVersion, requireMaster: Boolean) extends Out

sealed trait TransactionStartCompleted extends In
case class TransactionStartSucceed(transactionId: Long) extends TransactionStartCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}
case class TransactionStartFailed(result: OperationFailed.Value, message: Option[String]) extends TransactionStartCompleted



case class TransactionWrite(transactionId: Long, events: List[Event], requireMaster: Boolean) extends Out

sealed trait TransactionWriteCompleted extends In
case class TransactionWriteSucceed(transactionId: Long) extends TransactionWriteCompleted
case class TransactionWriteFailed(transactionId: Long,
                                  result: OperationFailed.Value,
                                  message: Option[String]) extends TransactionWriteCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}



case class TransactionCommit(transactionId: Long, requireMaster: Boolean) extends Out

sealed trait TransactionCommitCompleted extends In
case class TransactionCommitSucceed(transactionId: Long) extends TransactionCommitCompleted
case class TransactionCommitFailed(transactionId: Long,
                                   result: OperationFailed.Value,
                                   message: Option[String]) extends TransactionCommitCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}



case class SubscribeTo(stream: EventStream, resolveLinkTos: Boolean) extends Out

object SubscribeTo {
  def apply(stream: EventStream): SubscribeTo = SubscribeTo(stream = stream, resolveLinkTos = false)
}

sealed trait SubscribeCompleted extends In

case class SubscribeToAllCompleted(lastCommitPosition: Long) extends SubscribeCompleted

case class SubscribeToStreamCompleted(lastCommitPosition: Long, lastEventNumber: EventNumber) extends SubscribeCompleted

case class StreamEventAppeared(event: ResolvedEvent) extends In



case object UnsubscribeFromStream extends Out

case class SubscriptionDropped(reason: SubscriptionDropped.Value) extends In

object SubscriptionDropped extends Enumeration {
  val Unsubscribed, AccessDenied = Value
  val Default: Value = Unsubscribed
}



case object ScavengeDatabase extends Out

case object BadRequest extends In