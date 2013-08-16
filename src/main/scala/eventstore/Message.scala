package eventstore

import util.BetterToString

/**
 * @author Yaroslav Klymko
 */

sealed trait Message
sealed trait In extends Message
sealed trait Out extends Message
sealed trait InOut extends In with Out

private[eventstore] case object HeartbeatRequestCommand extends InOut
private[eventstore] case object HeartbeatResponseCommand extends InOut

private[eventstore] case object Ping extends InOut
private[eventstore] case object Pong extends InOut

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
//                 dataContentType: Int, // TODO
                 data: ByteString,
//                 metadataContentType: Int, // TODO
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
  val streamDeleted = "$streamDeleted" // TODO
  val streamCreated = "$streamCreated" // TODO
}

case class EventRecord(streamId: StreamId, number: EventNumber.Exact, event: Event)
case class ResolvedIndexedEvent(eventRecord: EventRecord, link: Option[EventRecord])
case class ResolvedEvent(eventRecord: EventRecord, link: Option[EventRecord], position: Position)



case class DeniedToRoute(externalTcpAddress: String,
                         externalTcpPort: Int,
                         externalHttpAddress: String,
                         externalHttpPort: Int) extends Message



case class AppendToStream(streamId: StreamId,
                          expVer: ExpectedVersion,
                          events: Seq[Event],
                          requireMaster: Boolean) extends Out

object AppendToStream {
  def apply(streamId: StreamId, expVer: ExpectedVersion, events: Seq[Event]): AppendToStream = AppendToStream(
    streamId = streamId,
    expVer = expVer,
    events = events,
    requireMaster = true)
}

sealed trait AppendToStreamCompleted extends In
case class AppendToStreamSucceed(firstEventNumber: Int) extends AppendToStreamCompleted
case class AppendToStreamFailed(reason: OperationFailed.Value, message: Option[String]) extends AppendToStreamCompleted



case class DeleteStream(streamId: StreamId,
                        expVer: ExpectedVersion, // TODO disallow NoVersion
                        requireMaster: Boolean) extends Out

sealed trait DeleteStreamCompleted extends In
case object DeleteStreamSucceed extends DeleteStreamCompleted
case class DeleteStreamFailed(reason: OperationFailed.Value, message: Option[String]) extends DeleteStreamCompleted



case class ReadEvent(streamId: StreamId, eventNumber: EventNumber, resolveLinkTos: Boolean) extends Out

sealed trait ReadEventCompleted extends In
case class ReadEventSucceed(event: ResolvedIndexedEvent) extends ReadEventCompleted
case class ReadEventFailed(reason: ReadEventFailed.Value, message: Option[String]) extends ReadEventCompleted
object ReadEventFailed extends Enumeration {
  val NotFound, NoStream, StreamDeleted, Error, AccessDenied = Value
}



object ReadDirection extends Enumeration {
  val Forward, Backward = Value
}


case class ReadStreamEvents(streamId: StreamId,
                            fromEventNumber: Int,
                            maxCount: Int,
                            resolveLinkTos: Boolean,
                            requireMaster: Boolean,
                            direction: ReadDirection.Value) extends Out {
  require(maxCount > 0, s"maxCount must > 0, but is $maxCount")
}

object ReadStreamEvents {
  def apply(streamId: StreamId,
            fromEventNumber: Int,
            maxCount: Int,
            direction: ReadDirection.Value): ReadStreamEvents = ReadStreamEvents(
    streamId = streamId,
    fromEventNumber = fromEventNumber,
    maxCount = maxCount,
    resolveLinkTos = false,
    requireMaster = true,
    direction = direction)
}

sealed trait ReadStreamEventsCompleted extends In {
  def direction: ReadDirection.Value
}

case class ReadStreamEventsSucceed(events: Seq[ResolvedIndexedEvent],
                                   nextEventNumber: Int,
                                   lastEventNumber: Int,
                                   endOfStream: Boolean,
                                   lastCommitPosition: Long,
                                   direction: ReadDirection.Value) extends ReadStreamEventsCompleted

case class ReadStreamEventsFailed(reason: ReadStreamEventsFailed.Value,
                                  message: Option[String],
                                  nextEventNumber: Int,
                                  lastEventNumber: Int,
                                  isEndOfStream: Boolean,
                                  lastCommitPosition: Long,
                                  direction: ReadDirection.Value) extends ReadStreamEventsCompleted

object ReadStreamEventsFailed extends Enumeration {
  val NoStream, StreamDeleted, Error, AccessDenied = Value
}



case class ReadAllEvents(position: Position,
                         maxCount: Int,
                         resolveLinkTos: Boolean,
                         requireMaster: Boolean,
                         direction: ReadDirection.Value) extends Out

object ReadAllEvents {
  def apply(position: Position, maxCount: Int, direction: ReadDirection.Value): ReadAllEvents = ReadAllEvents(
    position = position,
    maxCount = maxCount,
    resolveLinkTos = false,
    requireMaster = true,
    direction = direction)
}

sealed trait ReadAllEventsCompleted extends In {
  def position: Position
  def direction: ReadDirection.Value
}

case class ReadAllEventsSucceed(position: Position,
                                resolvedEvents: Seq[ResolvedEvent],
                                nextPosition: Position,
                                direction: ReadDirection.Value) extends ReadAllEventsCompleted

case class ReadAllEventsFailed(reason: ReadAllEventsFailed.Value,
                               message: Option[String],
                               position: Position,
                               direction: ReadDirection.Value) extends ReadAllEventsCompleted

object ReadAllEventsFailed extends Enumeration {
  val Error, AccessDenied = Value
}



case class TransactionStart(streamId: StreamId, expVer: ExpectedVersion, requireMaster: Boolean) extends Out

sealed trait TransactionStartCompleted extends In
case class TransactionStartSucceed(transactionId: Long) extends TransactionStartCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}
case class TransactionStartFailed(reason: OperationFailed.Value, message: Option[String]) extends TransactionStartCompleted



case class TransactionWrite(transactionId: Long, events: Seq[Event], requireMaster: Boolean) extends Out

sealed trait TransactionWriteCompleted extends In {
  def transactionId: Long
}
case class TransactionWriteSucceed(transactionId: Long) extends TransactionWriteCompleted
case class TransactionWriteFailed(transactionId: Long,
                                  reason: OperationFailed.Value,
                                  message: Option[String]) extends TransactionWriteCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}



case class TransactionCommit(transactionId: Long, requireMaster: Boolean) extends Out

sealed trait TransactionCommitCompleted extends In
case class TransactionCommitSucceed(transactionId: Long) extends TransactionCommitCompleted
case class TransactionCommitFailed(transactionId: Long,
                                   reason: OperationFailed.Value,
                                   message: Option[String]) extends TransactionCommitCompleted {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}



case class SubscribeTo(stream: EventStream, resolveLinkTos: Boolean) extends Out

object SubscribeTo {
  def apply(stream: EventStream): SubscribeTo = SubscribeTo(stream = stream, resolveLinkTos = false)
}

sealed trait SubscribeCompleted extends In

case class SubscribeToAllCompleted(lastCommit: Long) extends SubscribeCompleted {
  require(lastCommit > 0, s"lastCommit must > 0, but is $lastCommit") // TODO not sure about this restriction
}

case class SubscribeToStreamCompleted(lastCommit: Long, lastEventNumber: EventNumber) extends SubscribeCompleted {
  require(lastCommit > 0, s"lastCommit must > 0, but is $lastCommit")
}

case class StreamEventAppeared(resolvedEvent: ResolvedEvent) extends In



case object UnsubscribeFromStream extends Out
case class SubscriptionDropped(reason: SubscriptionDropped.Value) extends In
object SubscriptionDropped extends Enumeration {
  val Unsubscribed, AccessDenied = Value
}



case object ScavengeDatabase extends Out

case object BadRequest extends In