package eventstore.client





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
  InvalidTransaction = Value
}


case class NewEvent(eventId: ByteString,
                    eventType: Option[String], // TODO Optional? wtf ?
                    isJson: Boolean,
                    data: ByteString,
                    metadata: Option[ByteString])

case class EventRecord(streamId: String,
                       eventNumber: Int,
                       eventId: ByteString,
                       eventType: Option[String],
                       data: ByteString,
                       metadata: Option[ByteString])

case class ResolvedIndexedEvent(event: EventRecord,
                                link: Option[EventRecord])

case class ResolvedEvent(event: EventRecord,
                         link: Option[EventRecord],
                         commitPosition: Long,
                         preparePosition: Long)


case class DeniedToRoute(externalTcpAddress: String,
                         externalTcpPort: Int,
                         externalHttpAddress: String,
                         externalHttpPort: Int) extends Message


case class CreateStream(streamId: String,
                        requestId: Uuid, // change type to Uuid
                        metadata: ByteString, // TODO
                        allowForwarding: Boolean,
                        isJson: Boolean) extends Out


case class CreateStreamCompleted(result: OperationResult.Value, message: Option[String]) extends In


case class WriteEvents(streamId: String,
                       expVer: ExpectedVersion,
                       events: List[NewEvent],
                       allowForwarding: Boolean) extends Out

case class WriteEventsCompleted(result: OperationResult.Value,
                                message: Option[String],
                                firstEventNumber: Int) extends In



case class DeleteStream(streamId: String,
                        expVer: ExpectedVersion, // TODO disallow NoVersion
                        allowForwarding: Boolean) extends Out



case class DeleteStreamCompleted(result: OperationResult.Value, message: Option[String]) extends In



case class ReadEvent(streamId: String,
                     eventNumber: Int,
                     resolveLinkTos: Boolean) extends Out

case class ReadEventCompleted(result: ReadEventResult.Value,
                              event: ResolvedIndexedEvent) extends In



object ReadEventResult extends Enumeration {
  val Success, NotFound, NoStream, StreamDeleted = Value
}

case class ReadStreamEvents(streamId: String,
                            fromEventNumber: Int,
                            maxCount: Int,
                            resolveLinkTos: Boolean,
                            direction: ReadDirection.Value) extends Out

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
                            allowForwarding: Boolean) extends Out

case class TransactionStartCompleted(transactionId: Long,
                                     result: OperationResult.Value,
                                     message: Option[String]) extends In

case class TransactionWrite(transactionId: Long,
                            events: List[NewEvent],
                            allowForwarding: Boolean) extends Out

case class TransactionWriteCompleted(transactionId: Long,
                                     result: OperationResult.Value,
                                     message: Option[String]) extends In

case class TransactionCommit(transactionId: Long,
                             allowForwarding: Boolean) extends Out

case class TransactionCommitCompleted(transactionId: Long,
                                      result: OperationResult.Value,
                                      message: Option[String]) extends In



case class SubscribeToStream(streamId: String, resolveLinkTos: Boolean) extends Out
case class SubscriptionConfirmation(lastCommitPosition: Long, lastEventNumber: Option[Int]) extends In
case class StreamEventAppeared(event: ResolvedEvent) extends In



case object UnsubscribeFromStream extends Out
case object SubscriptionDropped extends In



case object ScavengeDatabase extends Out

case object BadRequest extends In


object Message {
  def deserialize(in: ByteString)(implicit deserializer: ByteString => In): In = deserializer(in)
  def serialize[T <: Out](out: T)(implicit serializer: T => ByteString): ByteString = serialize(out)
}
