package eventstore

import util.BetterToString

/**
 * @author Yaroslav Klymko
 */

sealed trait Message
sealed trait In extends Message
sealed trait Out extends Message

case object HeartbeatRequestCommand extends In
case object HeartbeatResponseCommand extends Out

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

object OperationResult extends Enumeration {
  val Success,
  PrepareTimeout,
  CommitTimeout,
  ForwardTimeout,
  WrongExpectedVersion,
  StreamDeleted,
  InvalidTransaction,
  AccessDenied = Value
}


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

case class EventRecord(streamId: String, eventNumber: Int, event: Event)

case class ResolvedIndexedEvent(eventRecord: EventRecord, link: Option[EventRecord])

case class ResolvedEvent(event: EventRecord,
                         link: Option[EventRecord],
                         commitPosition: Long,
                         preparePosition: Long)


case class DeniedToRoute(externalTcpAddress: String,
                         externalTcpPort: Int,
                         externalHttpAddress: String,
                         externalHttpPort: Int) extends Message


case class AppendToStream(streamId: String,
                          expVer: ExpectedVersion,
                          events: List[Event],
                          requireMaster: Boolean) extends Out

object AppendToStream {
  def apply(streamId: String, expVer: ExpectedVersion, events: List[Event]): AppendToStream = AppendToStream(
    streamId = streamId,
    expVer = expVer,
    events = events,
    requireMaster = true)
}

case class AppendToStreamCompleted(result: OperationResult.Value,
                                   message: Option[String],
                                   firstEventNumber: Int) extends In


case class DeleteStream(streamId: String,
                        expVer: ExpectedVersion, // TODO disallow NoVersion
                        requireMaster: Boolean) extends Out


case class DeleteStreamCompleted(result: OperationResult.Value, message: Option[String]) extends In


case class ReadEvent(streamId: String,
                     eventNumber: Int,
                     resolveLinkTos: Boolean) extends Out

sealed trait ReadEventCompleted extends In
case class ReadEventSucceed(event: ResolvedIndexedEvent) extends ReadEventCompleted
case class ReadEventFailed(reason: ReadEventFailed.Value) extends ReadEventCompleted
object ReadEventFailed extends Enumeration {
  val NotFound, NoStream, StreamDeleted, Error, AccessDenied = Value
}


object ReadEventResult extends Enumeration {
  val Success, NotFound, NoStream, StreamDeleted, Error, AccessDenied = Value
}

case class ReadStreamEvents(streamId: String,
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
  val Success, NoStream, StreamDeleted, NotModified, Error = Value
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


case class TransactionStart(streamId: String,
                            expVer: ExpectedVersion,
                            requireMaster: Boolean) extends Out

case class TransactionStartCompleted(transactionId: Long,
                                     result: OperationResult.Value,
                                     message: Option[String]) extends In

case class TransactionWrite(transactionId: Long,
                            events: List[Event],
                            requireMaster: Boolean) extends Out

case class TransactionWriteCompleted(transactionId: Long,
                                     result: OperationResult.Value,
                                     message: Option[String]) extends In

case class TransactionCommit(transactionId: Long,
                             requireMaster: Boolean) extends Out

case class TransactionCommitCompleted(transactionId: Long,
                                      result: OperationResult.Value,
                                      message: Option[String]) extends In


case class SubscribeToStream(streamId: String, resolveLinkTos: Boolean) extends Out

object SubscribeToStream {
  def apply(streamId: String): SubscribeToStream = SubscribeToStream(
    streamId = streamId,
    resolveLinkTos = false)
}
case class SubscriptionConfirmation(lastCommitPosition: Long, lastEventNumber: Option[Int]) extends In
case class StreamEventAppeared(event: ResolvedEvent) extends In

case object UnsubscribeFromStream extends Out

case class SubscriptionDropped(reason: SubscriptionDropped.Reason.Value) extends In


object SubscriptionDropped {

//  def apply(): SubscriptionDropped = SubscriptionDropped(Reason.Unsubscribed)

  object Reason extends Enumeration {
    val Unsubscribed, AccessDenied = Value
    val Default = Unsubscribed
  }

}


case object ScavengeDatabase extends Out

case object BadRequest extends In


object Message {
  def deserialize(in: ByteString)(implicit deserializer: ByteString => In): In = deserializer(in)

  def serialize[T <: Out](out: T)(implicit serializer: T => ByteString): ByteString = serializer(out)
}
