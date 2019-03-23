package eventstore
package core

import scala.collection.JavaConverters._
import constants.MaxBatchSize

sealed trait OutLike {
  def out: Out
}

@SerialVersionUID(1L)
final case class WithCredentials(
  out: Out,
  credentials: UserCredentials
) extends OutLike

sealed trait Message
sealed trait In  extends Message
sealed trait Out extends Message with OutLike {

  final def out: Out = this
  final def withCredentials(login: String, password: String): WithCredentials =
    withCredentials(UserCredentials(login = login, password = password))

  final def withCredentials(x: UserCredentials): WithCredentials = WithCredentials(this, x)

}

sealed trait InOut extends In with Out

@SerialVersionUID(1L) private[eventstore] case object HeartbeatRequest extends InOut
@SerialVersionUID(1L) private[eventstore] case object HeartbeatResponse extends InOut

@SerialVersionUID(1L) case object Ping extends InOut
@SerialVersionUID(1L) case object Pong extends InOut

@SerialVersionUID(1L)
final case class IdentifyClient(
  version:        Int,
  connectionName: Option[String]
) extends Out {
  require(version >= 0, s"version must be >= 0, but is $version")
}

@SerialVersionUID(1L)
case object ClientIdentified extends In

@SerialVersionUID(1L)
final case class WriteEvents(
  streamId:        EventStream.Id,
  events:          List[EventData],
  expectedVersion: ExpectedVersion,
  requireMaster:   Boolean
) extends Out

object WriteEvents {

  object StreamMetadata {
    def apply(
      streamId:        EventStream.Metadata,
      data:            Content,
      eventId:         Uuid,
      expectedVersion: ExpectedVersion,
      requireMaster:   Boolean
    ): WriteEvents =
      WriteEvents(streamId, List(EventData.StreamMetadata(data, eventId)), expectedVersion, requireMaster)
  }
}

@SerialVersionUID(1L)
final case class WriteEventsCompleted(
  numbersRange: Option[EventNumber.Range],
  position:     Option[Position.Exact]
) extends In

@SerialVersionUID(1L)
final case class DeleteStream(
  streamId:        EventStream.Id,
  expectedVersion: ExpectedVersion.Existing,
  hard:            Boolean,
  requireMaster:   Boolean
) extends Out

@SerialVersionUID(1L)
final case class DeleteStreamCompleted(
  position: Option[Position.Exact]
) extends In

@SerialVersionUID(1L)
final case class TransactionStart(
  streamId:        EventStream.Id,
  expectedVersion: ExpectedVersion,
  requireMaster:   Boolean
) extends Out

@SerialVersionUID(1L)
final case class TransactionStartCompleted(
  transactionId: Long
) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L)
final case class TransactionWrite(
  transactionId: Long,
  events:        List[EventData],
  requireMaster: Boolean
) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L)
final case class TransactionWriteCompleted(
  transactionId: Long
) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L)
final case class TransactionCommit(
  transactionId: Long,
  requireMaster: Boolean
) extends Out {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L)
final case class TransactionCommitCompleted(
  transactionId: Long,
  numbersRange:  Option[EventNumber.Range],
  position:      Option[Position.Exact]
) extends In {
  require(transactionId >= 0, s"transactionId must be >= 0, but is $transactionId")
}

@SerialVersionUID(1L)
final case class ReadEvent(
  streamId:       EventStream.Id,
  eventNumber:    EventNumber,
  resolveLinkTos: Boolean,
  requireMaster:  Boolean
) extends Out

object ReadEvent {
  object StreamMetadata {
    def apply(
      streamId:       EventStream.Metadata,
      eventNumber:    EventNumber,
      resolveLinkTos: Boolean,
      requireMaster:  Boolean
    ): ReadEvent = ReadEvent(streamId, eventNumber, resolveLinkTos, requireMaster)
  }
}

@SerialVersionUID(1L)
final case class ReadEventCompleted(
  event: Event
) extends In

@SerialVersionUID(1L)
final case class ReadStreamEvents(
  streamId:       EventStream.Id,
  fromNumber:     EventNumber,
  maxCount:       Int,
  direction:      ReadDirection,
  resolveLinkTos: Boolean,
  requireMaster:  Boolean
) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
  require(
    direction != ReadDirection.Forward || fromNumber != EventNumber.Last,
    s"fromNumber must not be EventNumber.Last"
  )
}

@SerialVersionUID(1L)
final case class ReadStreamEventsCompleted(
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

@SerialVersionUID(1L)
final case class ReadAllEvents(
  fromPosition:   Position,
  maxCount:       Int,
  direction:      ReadDirection,
  resolveLinkTos: Boolean,
  requireMaster:  Boolean
) extends Out {
  require(maxCount > 0, s"maxCount must be > 0, but is $maxCount")
  require(maxCount <= MaxBatchSize, s"maxCount must be <= $MaxBatchSize, but is $maxCount")
}

@SerialVersionUID(1L)
final case class ReadAllEventsCompleted(
  events:       List[IndexedEvent],
  position:     Position.Exact,
  nextPosition: Position.Exact,
  direction:    ReadDirection
) extends In {
  require(events.size <= MaxBatchSize, s"events.size must be <= $MaxBatchSize, but is ${events.size}")

  def eventsJava: java.util.List[IndexedEvent] = events.asJava
}

object PersistentSubscription {

  type PSS = settings.PersistentSubscriptionSettings

  @SerialVersionUID(1L)
  final case class Create(
    streamId: EventStream.Id,
    groupName: String,
    settings: PSS
  ) extends Out {
    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L) case object CreateCompleted extends In

  @SerialVersionUID(1L)
  final case class Update(
    streamId:  EventStream.Id,
    groupName: String,
    settings:  PSS
  ) extends Out {
    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L) case object UpdateCompleted extends In

  @SerialVersionUID(1L)
  final case class Delete(
    streamId: EventStream.Id,
    groupName: String
  ) extends Out {
    require(groupName != null, "groupName must not be null")
    require(groupName.nonEmpty, "groupName must not be empty")
  }

  @SerialVersionUID(1L) case object DeleteCompleted extends In

  @SerialVersionUID(1L)
  final case class Ack(
    subscriptionId: String,
    eventIds: List[Uuid]
  ) extends Out {
    require(subscriptionId != null, "subscriptionId must not be null")
    require(subscriptionId.nonEmpty, "subscriptionId must not be empty")
    require(eventIds.nonEmpty, "eventIds must not be empty")
  }

  @SerialVersionUID(1L)
  final case class Nak(
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

  @SerialVersionUID(1L)
  final case class Connect(
    streamId:   EventStream.Id,
    groupName:  String,
    bufferSize: Int = 10
  ) extends Out

  @SerialVersionUID(1L)
  final case class Connected(
    subscriptionId:  String,
    lastCommit:      Long,
    lastEventNumber: Option[EventNumber.Exact]
  ) extends In

  @SerialVersionUID(1L)
  final case class EventAppeared(
    event: Event
  ) extends In

}

@SerialVersionUID(1L)
final case class SubscribeTo(
  stream: EventStream,
  resolveLinkTos: Boolean
) extends Out

sealed trait SubscribeCompleted extends In

@SerialVersionUID(1L)
final case class SubscribeToAllCompleted(
  lastCommit: Long
) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}


@SerialVersionUID(1L)
final case class SubscribeToStreamCompleted(
  lastCommit:      Long,
  lastEventNumber: Option[EventNumber.Exact] = None
) extends SubscribeCompleted {
  require(lastCommit >= 0, s"lastCommit must be >= 0, but is $lastCommit")
}

@SerialVersionUID(1L)
final case class StreamEventAppeared(
  event: IndexedEvent
) extends In

@SerialVersionUID(1L)
case object Unsubscribe extends Out {
  /**
   * Java API
   */
  def getInstance: Unsubscribe.type = this
}
@SerialVersionUID(1L)
case object Unsubscribed extends In {
  /**
   * Java API
   */
  def getInstance: Unsubscribed.type = this
}

@SerialVersionUID(1L)
case object ScavengeDatabase extends Out {
  /**
   * Java API
   */
  def getInstance: ScavengeDatabase.type = this
}

@SerialVersionUID(1L)
final case class ScavengeDatabaseResponse(
  scavengeId: Option[String]
) extends In

@SerialVersionUID(1L)
case object Authenticate extends Out {
  /**
   * Java API
   */
  def getInstance: Authenticate.type = this
}

@SerialVersionUID(1L)
case object Authenticated extends In {
  /**
   * Java API
   */
  def getInstance: Authenticated.type = this
}