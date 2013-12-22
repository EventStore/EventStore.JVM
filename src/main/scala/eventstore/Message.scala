package eventstore

import scala.collection.JavaConverters._

sealed trait OutLike

case class WithCredentials(message: Out, credentials: UserCredentials) extends OutLike

sealed trait Message
sealed trait In extends Message

sealed trait Out extends Message with OutLike {
  def withCredentials(login: String, password: String): WithCredentials = WithCredentials(
    message = this,
    credentials = UserCredentials(login = login, password = password))
}
sealed trait InOut extends In with Out

case object HeartbeatRequest extends InOut
case object HeartbeatResponse extends InOut

case object Ping extends InOut
case object Pong extends InOut

//case object PrepareAck extends Message
//case object CommitAck extends Message

//case object SlaveAssignment extends Message
//case object CloneAssignment extends Message

//case object SubscribeReplica extends Message
//case object CreateChunk extends Message
//case object PhysicalChunkBulk extends Message
//case object LogicalChunkBulk extends Message

//case class DeniedToRoute(
//  externalTcpAddress: String,
//  externalTcpPort: Int,
//  externalHttpAddress: String,
//  externalHttpPort: Int) extends Message

case class WriteEvents(
  streamId: EventStream.Id,
  events: Seq[EventData],
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster: Boolean = true) extends Out

case class WriteEventsCompleted(firstEventNumber: EventNumber.Exact) extends In

// TODO check softDelete
case class DeleteStream(
  streamId: EventStream.Id,
  expectedVersion: ExpectedVersion.Existing = ExpectedVersion.Any,
  requireMaster: Boolean = true) extends Out

case object DeleteStreamCompleted extends In

case class TransactionStart(
  streamId: EventStream.Id,
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster: Boolean = true) extends Out

// TODO what if 2 transactions started at same time?
case class TransactionStartCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionWrite(transactionId: Long, events: Seq[EventData], requireMaster: Boolean = true) extends Out { // TODO Seq => List
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionWriteCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionCommit(transactionId: Long, requireMaster: Boolean = true) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionCommitCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class ReadEvent(
  streamId: EventStream.Id,
  eventNumber: EventNumber,
  resolveLinkTos: Boolean = false,
  requireMaster: Boolean = true) extends Out

case class ReadEventCompleted(event: Event) extends In

case class ReadStreamEvents(
    streamId: EventStream.Id,
    fromNumber: EventNumber = EventNumber.First,
    maxCount: Int = 500,
    direction: ReadDirection.Value = ReadDirection.Forward,
    resolveLinkTos: Boolean = false,
    requireMaster: Boolean = true) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
  require(
    direction != ReadDirection.Forward || fromNumber != EventNumber.Last,
    s"fromNumber must not be EventNumber.Last")
}

case class ReadStreamEventsCompleted(
    events: Seq[Event], // TODO use concrete collection
    nextEventNumber: EventNumber,
    lastEventNumber: EventNumber.Exact,
    endOfStream: Boolean,
    lastCommitPosition: Long,
    direction: ReadDirection.Value) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")
  require(
    direction != ReadDirection.Forward || nextEventNumber != EventNumber.Last,
    s"lastEventNumber must not be EventNumber.Last")

  def eventsJava: java.util.List[Event] = events.asJava
}

case class ReadAllEvents(
    fromPosition: Position,
    maxCount: Int,
    direction: ReadDirection.Value = ReadDirection.Forward,
    resolveLinkTos: Boolean = false,
    requireMaster: Boolean = true) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
}

case class ReadAllEventsCompleted(
    events: Seq[IndexedEvent],
    position: Position.Exact,
    nextPosition: Position.Exact,
    direction: ReadDirection.Value) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")

  def eventsJava: java.util.List[IndexedEvent] = events.asJava
}

case class SubscribeTo(stream: EventStream, resolveLinkTos: Boolean = false) extends Out

// TODO what if sender is dead, need to close subscription.
sealed trait SubscribeCompleted extends In

case class SubscribeToAllCompleted(lastCommit: Long) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}

case class SubscribeToStreamCompleted(
    lastCommit: Long,
    lastEventNumber: Option[EventNumber.Exact] = None) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}

case class StreamEventAppeared(event: IndexedEvent) extends In

case object UnsubscribeFromStream extends Out
case object UnsubscribeCompleted extends In

case object ScavengeDatabase extends Out

case object BadRequest extends In