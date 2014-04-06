package eventstore

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

sealed trait OutLike {
  def out: Out
}

case class WithCredentials(out: Out, credentials: UserCredentials) extends OutLike

sealed trait Message
sealed trait In extends Message

sealed trait Out extends Message with OutLike {
  def out = this

  def withCredentials(x: UserCredentials): WithCredentials = WithCredentials(this, x)

  def withCredentials(login: String, password: String): WithCredentials =
    withCredentials(UserCredentials(login = login, password = password))
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

case class WriteEvents(
  streamId: EventStream.Id,
  events: List[EventData],
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster: Boolean = true) extends Out

object WriteEvents {

  object Metadata {
    def apply(
      streamId: EventStream.Id,
      data: Content,
      expectedVersion: ExpectedVersion = ExpectedVersion.Any,
      requireMaster: Boolean = true): WriteEvents = WriteEvents(
      streamId match {
        case x: EventStream.Metadata    => x
        case x: EventStream.HasMetadata => x.metadata
      },
      List(EventData.StreamMetadata(data)),
      expectedVersion = expectedVersion,
      requireMaster = requireMaster)
  }
}

case class WriteEventsCompleted(numbersRange: Option[EventNumber.Range] = None) extends In

// TODO check softDelete
case class DeleteStream(
  streamId: EventStream.Id,
  expectedVersion: ExpectedVersion.Existing = ExpectedVersion.Any,
  requireMaster: Boolean = true,
  hardDelete: Boolean = false /*TODO*/ ) extends Out

case object DeleteStreamCompleted extends In {
  /**
   * Java API
   */
  def getInstance = this
}

case class TransactionStart(
  streamId: EventStream.Id,
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster: Boolean = true) extends Out

// TODO what if 2 transactions started at same time?
case class TransactionStartCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionWrite(transactionId: Long, events: List[EventData], requireMaster: Boolean = true) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionWriteCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionCommit(transactionId: Long, requireMaster: Boolean = true) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class TransactionCommitCompleted(transactionId: Long, numbersRange: Option[EventNumber.Range] = None) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

case class ReadEvent(
  streamId: EventStream.Id,
  eventNumber: EventNumber = EventNumber.First,
  resolveLinkTos: Boolean = false,
  requireMaster: Boolean = true) extends Out

case class ReadEventCompleted(event: Event) extends In

case class ReadStreamEvents(
    streamId: EventStream.Id,
    fromNumber: EventNumber = EventNumber.First,
    maxCount: Int = Settings.ReadBatchSize,
    direction: ReadDirection = ReadDirection.Forward,
    resolveLinkTos: Boolean = false,
    requireMaster: Boolean = true) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
  require(
    direction != ReadDirection.Forward || fromNumber != EventNumber.Last,
    s"fromNumber must not be EventNumber.Last")
}

case class ReadStreamEventsCompleted(
    events: List[Event],
    nextEventNumber: EventNumber,
    lastEventNumber: EventNumber.Exact,
    endOfStream: Boolean,
    lastCommitPosition: Long,
    direction: ReadDirection) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")
  require(
    direction != ReadDirection.Forward || nextEventNumber != EventNumber.Last,
    s"lastEventNumber must not be EventNumber.Last")

  def eventsJava: java.util.List[Event] = events.asJava
}

case class ReadAllEvents(
    fromPosition: Position = Position.First,
    maxCount: Int = Settings.ReadBatchSize,
    direction: ReadDirection = ReadDirection.Forward,
    resolveLinkTos: Boolean = false,
    requireMaster: Boolean = true) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
}

case class ReadAllEventsCompleted(
    events: List[IndexedEvent],
    position: Position.Exact,
    nextPosition: Position.Exact,
    direction: ReadDirection) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")

  def eventsJava: java.util.List[IndexedEvent] = events.asJava
}

case class SubscribeTo(stream: EventStream, resolveLinkTos: Boolean = false) extends Out

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

case object Unsubscribe extends Out {
  /**
   * Java API
   */
  def getInstance = this
}
case object UnsubscribeCompleted extends In {
  /**
   * Java API
   */
  def getInstance = this
}

case object ScavengeDatabase extends Out {
  /**
   * Java API
   */
  def getInstance = this
}

case class ScavengeDatabaseCompleted(totalTime: FiniteDuration, totalSpaceSaved: Long) extends In

case object Authenticate extends Out {
  /**
   * Java API
   */
  def getInstance = this
}
case object Authenticated extends In {
  /**
   * Java API
   */
  def getInstance = this
}