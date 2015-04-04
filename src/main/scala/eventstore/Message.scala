package eventstore

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

sealed trait OutLike {
  def out: Out
}

@SerialVersionUID(1L) case class WithCredentials(out: Out, credentials: UserCredentials) extends OutLike

sealed trait Message
sealed trait In extends Message

sealed trait Out extends Message with OutLike {
  def out = this

  def withCredentials(x: UserCredentials): WithCredentials = WithCredentials(this, x)

  def withCredentials(login: String, password: String): WithCredentials =
    withCredentials(UserCredentials(login = login, password = password))
}

sealed trait InOut extends In with Out

@SerialVersionUID(1L) private[eventstore] case object HeartbeatRequest extends InOut
@SerialVersionUID(1L) private[eventstore] case object HeartbeatResponse extends InOut

@SerialVersionUID(1L) case object Ping extends InOut
@SerialVersionUID(1L) case object Pong extends InOut

@SerialVersionUID(1L) case class WriteEvents(
  streamId:        EventStream.Id,
  events:          List[EventData],
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster:   Boolean         = Settings.Default.requireMaster
) extends Out

object WriteEvents {

  object StreamMetadata {
    def apply(
      streamId:        EventStream.Metadata,
      data:            Content,
      expectedVersion: ExpectedVersion      = ExpectedVersion.Any,
      requireMaster:   Boolean              = Settings.Default.requireMaster
    ): WriteEvents = WriteEvents(
      streamId = streamId,
      events = List(EventData.StreamMetadata(data)),
      expectedVersion = expectedVersion,
      requireMaster = requireMaster
    )
  }
}

@SerialVersionUID(1L) case class WriteEventsCompleted(
  numbersRange: Option[EventNumber.Range] = None,
  position:     Option[Position.Exact]    = None
) extends In

@SerialVersionUID(1L) case class DeleteStream(
  streamId:        EventStream.Id,
  expectedVersion: ExpectedVersion.Existing = ExpectedVersion.Any,
  hard:            Boolean                  = false,
  requireMaster:   Boolean                  = Settings.Default.requireMaster
) extends Out

@SerialVersionUID(1L) case class DeleteStreamCompleted(position: Option[Position.Exact] = None) extends In

@SerialVersionUID(1L) case class TransactionStart(
  streamId:        EventStream.Id,
  expectedVersion: ExpectedVersion = ExpectedVersion.Any,
  requireMaster:   Boolean         = Settings.Default.requireMaster
) extends Out

@SerialVersionUID(1L) case class TransactionStartCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L) case class TransactionWrite(
    transactionId: Long,
    events:        List[EventData],
    requireMaster: Boolean         = Settings.Default.requireMaster
) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L) case class TransactionWriteCompleted(transactionId: Long) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L) case class TransactionCommit(
    transactionId: Long,
    requireMaster: Boolean = Settings.Default.requireMaster
) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L) case class TransactionCommitCompleted(
    transactionId: Long,
    numbersRange:  Option[EventNumber.Range] = None,
    position:      Option[Position.Exact]    = None
) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L) case class ReadEvent(
  streamId:       EventStream.Id,
  eventNumber:    EventNumber    = EventNumber.First,
  resolveLinkTos: Boolean        = Settings.Default.resolveLinkTos,
  requireMaster:  Boolean        = Settings.Default.requireMaster
) extends Out

object ReadEvent {
  object StreamMetadata {
    def apply(
      streamId:       EventStream.Metadata,
      resolveLinkTos: Boolean              = Settings.Default.resolveLinkTos,
      requireMaster:  Boolean              = Settings.Default.requireMaster
    ): ReadEvent = {
      ReadEvent(streamId, EventNumber.Last, resolveLinkTos = resolveLinkTos, requireMaster = requireMaster)
    }
  }
}

@SerialVersionUID(1L) case class ReadEventCompleted(event: Event) extends In

@SerialVersionUID(1L) case class ReadStreamEvents(
    streamId:       EventStream.Id,
    fromNumber:     EventNumber    = EventNumber.First,
    maxCount:       Int            = Settings.Default.readBatchSize,
    direction:      ReadDirection  = ReadDirection.Forward,
    resolveLinkTos: Boolean        = Settings.Default.resolveLinkTos,
    requireMaster:  Boolean        = Settings.Default.requireMaster
) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
  require(
    direction != ReadDirection.Forward || fromNumber != EventNumber.Last,
    s"fromNumber must not be EventNumber.Last"
  )
}

@SerialVersionUID(1L) case class ReadStreamEventsCompleted(
    events:             List[Event],
    nextEventNumber:    EventNumber,
    lastEventNumber:    EventNumber.Exact,
    endOfStream:        Boolean,
    lastCommitPosition: Long,
    direction:          ReadDirection
) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")
  require(
    direction != ReadDirection.Forward || nextEventNumber != EventNumber.Last,
    s"lastEventNumber must not be EventNumber.Last"
  )

  def eventsJava: java.util.List[Event] = events.asJava
}

@SerialVersionUID(1L) case class ReadAllEvents(
    fromPosition:   Position      = Position.First,
    maxCount:       Int           = Settings.Default.readBatchSize,
    direction:      ReadDirection = ReadDirection.Forward,
    resolveLinkTos: Boolean       = Settings.Default.resolveLinkTos,
    requireMaster:  Boolean       = Settings.Default.requireMaster
) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
}

@SerialVersionUID(1L) case class ReadAllEventsCompleted(
    events:       List[IndexedEvent],
    position:     Position.Exact,
    nextPosition: Position.Exact,
    direction:    ReadDirection
) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")

  def eventsJava: java.util.List[IndexedEvent] = events.asJava
}

object PersistentSubscription {

  /**
   * Java API
   */
  def create(streamId: EventStream.Id, groupName: String, settings: PersistentSubscriptionSettings): Create = {
    Create(streamId, groupName, settings)
  }

  /**
   * Java API
   */
  def update(streamId: EventStream.Id, groupName: String, settings: PersistentSubscriptionSettings): Update = {
    Update(streamId, groupName, settings)
  }

  /**
   * Java API
   */
  def delete(streamId: EventStream.Id, groupName: String): Delete = {
    Delete(streamId, groupName)
  }

  @SerialVersionUID(1L)
  case class Create(
      streamId: EventStream.Id, groupName: String,
      settings: PersistentSubscriptionSettings = PersistentSubscriptionSettings.Default
  ) extends Out {

    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L)
  case object CreateCompleted extends In

  @SerialVersionUID(1L)
  case class Update(
      streamId:  EventStream.Id,
      groupName: String,
      settings:  PersistentSubscriptionSettings = PersistentSubscriptionSettings.Default
  ) extends Out {

    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L)
  case object UpdateCompleted extends In

  @SerialVersionUID(1L)
  case class Delete(streamId: EventStream.Id, groupName: String) extends Out {
    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L)
  case object DeleteCompleted extends In

  @SerialVersionUID(1L)
  case class Ack(subscriptionId: String, eventIds: List[Uuid]) extends Out {
    require(subscriptionId != null, "subscriptionId must not be null")
    require(subscriptionId.nonEmpty, "subscriptionId must not be empty")
    require(eventIds.nonEmpty, "eventIds must not be empty")
  }

  @SerialVersionUID(1L)
  case class Nak(
      subscriptionId: String,
      eventIds:       List[Uuid],
      action:         Nak.Action,
      message:        Option[String] = None
  ) extends Out {

    require(subscriptionId != null, "subscriptionId must not be null")
    require(subscriptionId.nonEmpty, "subscriptionId must not be empty")
    require(eventIds.nonEmpty, "eventIds must not be empty")
  }

  object Nak {
    sealed trait Action
    object Action {
      @SerialVersionUID(1L) case object Unknown extends Action
      @SerialVersionUID(1L) case object Park extends Action
      @SerialVersionUID(1L) case object Retry extends Action
      @SerialVersionUID(1L) case object Skip extends Action
      @SerialVersionUID(1L) case object Stop extends Action
    }
  }

  @SerialVersionUID(1L) case class Connect(
    streamId:   EventStream.Id,
    groupName:  String,
    bufferSize: Int            = 10
  ) extends Out

  @SerialVersionUID(1L) case class Connected(
    subscriptionId:  String,
    lastCommit:      Long,
    lastEventNumber: Option[EventNumber.Exact]
  ) extends In

  @SerialVersionUID(1L) case class EventAppeared(event: Event) extends In
}

@SerialVersionUID(1L) case class SubscribeTo(stream: EventStream, resolveLinkTos: Boolean = Settings.Default.resolveLinkTos) extends Out

sealed trait SubscribeCompleted extends In

@SerialVersionUID(1L) case class SubscribeToAllCompleted(lastCommit: Long) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}

@SerialVersionUID(1L) case class SubscribeToStreamCompleted(
    lastCommit:      Long,
    lastEventNumber: Option[EventNumber.Exact] = None
) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}

@SerialVersionUID(1L) case class StreamEventAppeared(event: IndexedEvent) extends In

@SerialVersionUID(1L) case object Unsubscribe extends Out {
  /**
   * Java API
   */
  def getInstance = this
}
@SerialVersionUID(1L) case object Unsubscribed extends In {
  /**
   * Java API
   */
  def getInstance = this
}

@SerialVersionUID(1L) case object ScavengeDatabase extends Out {
  /**
   * Java API
   */
  def getInstance = this
}

@SerialVersionUID(1L) case class ScavengeDatabaseCompleted(totalTime: FiniteDuration, totalSpaceSaved: Long) extends In

@SerialVersionUID(1L) case object Authenticate extends Out {
  /**
   * Java API
   */
  def getInstance = this
}

@SerialVersionUID(1L) case object Authenticated extends In {
  /**
   * Java API
   */
  def getInstance = this
}