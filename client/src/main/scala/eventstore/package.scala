package object eventstore {

  def randomUuid: Uuid = eventstore.core.util.uuid.randomUuid

  private[eventstore] val sinceVersion = "7.0.0"

  private[eventstore] def deprecationMsg(name: String, fromPkg: String, toPkg: String) =
    s"$name has been moved from $fromPkg.$name to $toPkg.$name. " +
    s"Please update your imports, as this deprecated type alias will be " +
    s"removed in a future version of EventStore.JVM."

  private[eventstore] def akkaMsg(name: String) =
    deprecationMsg(name, "eventstore", "eventstore.akka")

  /// ************************************** Akka ************************************** ///

  import eventstore.{akka => a}

  @deprecated(akkaMsg("EsConnection"), since = sinceVersion)
  type EsConnection        = a.EsConnection
  val EsConnection         = a.EsConnection

  @deprecated(akkaMsg("EventStoreExtension"), since = sinceVersion)
  type EventStoreExtension = a.EventStoreExtension
  val EventStoreExtension  = a.EventStoreExtension

  @deprecated(akkaMsg("OverflowStrategy"), since = sinceVersion)
  type OverflowStrategy    = a.OverflowStrategy
  val OverflowStrategy     = a.OverflowStrategy

  @deprecated(akkaMsg("EsTransaction"), since = sinceVersion)
  type EsTransaction       = a.EsTransaction
  val EsTransaction        = a.EsTransaction

  @deprecated(akkaMsg("SubscriptionObserver"), since = sinceVersion)
  type SubscriptionObserver[T] = a.SubscriptionObserver[T]

  @deprecated(akkaMsg("ProjectionsClient"), since = sinceVersion)
  type ProjectionsClient   = a.ProjectionsClient
  val ProjectionsClient    = a.ProjectionsClient

  @deprecated(akkaMsg("PersistentSubscriptionActor"), since = sinceVersion)
  val PersistentSubscriptionActor  = a.PersistentSubscriptionActor

  @deprecated(akkaMsg("LiveProcessingStarted"), since = sinceVersion)
  type LiveProcessingStarted = a.LiveProcessingStarted.type
  val LiveProcessingStarted  = a.LiveProcessingStarted

  @deprecated(akkaMsg("Settings"), since = sinceVersion)
  type Settings              = a.Settings
  val Settings               = a.Settings

  @deprecated(akkaMsg("HttpSettings"), since = sinceVersion)
  type HttpSettings          = a.HttpSettings
  val HttpSettings           = a.HttpSettings

  /// ************************************** Core ************************************** ///

  import eventstore.{core => c}
  import eventstore.core.{settings => cs}

  type Uuid            = c.Uuid
  type EventStream     = c.EventStream
  val  EventStream     = c.EventStream
  type EventNumber     = c.EventNumber
  val  EventNumber     = c.EventNumber
  type Position        = c.Position
  val  Position        = c.Position
  type ExpectedVersion = c.ExpectedVersion
  val  ExpectedVersion = c.ExpectedVersion
  type ReadDirection   = c.ReadDirection
  val  ReadDirection   = c.ReadDirection
  type Event           = c.Event
  val  Event           = c.Event
  type EventRecord     = c.EventRecord
  val  EventRecord     = c.EventRecord
  type ResolvedEvent   = c.ResolvedEvent
  val  ResolvedEvent   = c.ResolvedEvent
  type IndexedEvent    = c.IndexedEvent
  val  IndexedEvent    = c.IndexedEvent
  type EventData       = c.EventData
  val EventData        = c.EventData
  type Content         = c.Content
  val  Content         = c.Content
  type ContentType     = c.ContentType
  val  ContentType     = c.ContentType
  val SystemEventType  = c.SystemEventType

  /// Settings

  type PersistentSubscriptionSettings = cs.PersistentSubscriptionSettings
  object PersistentSubscriptionSettings {
    import com.typesafe.config.{Config, ConfigFactory}

    lazy val Default: PersistentSubscriptionSettings        = apply(ConfigFactory.load())
    def apply(conf: Config): PersistentSubscriptionSettings = cs.PersistentSubscriptionSettings(conf)
  }

  private lazy val RequireMaster: Boolean  = a.Settings.Default.requireMaster
  private lazy val ResolveLinkTos: Boolean = a.Settings.Default.resolveLinkTos
  private lazy val ReadBatchSize: Int      = a.Settings.Default.readBatchSize

  /// Messages

  type ClassTags[O, I]  = c.ClassTags[O, I]
  val  ClassTags        = c.ClassTags

  type In               = c.In
  type Out              = c.Out
  type OutLike          = c.OutLike
  type WithCredentials  = c.WithCredentials
  val  WithCredentials  = c.WithCredentials
  val  Ping             = c.Ping
  val  Pong             = c.Pong
  type IdentifyClient   = c.IdentifyClient
  val  IdentifyClient   = c.IdentifyClient
  val  ClientIdentified = c.ClientIdentified

  type WriteEvents = c.WriteEvents
  object WriteEvents {

    def unapply(arg: WriteEvents): Option[(EventStream.Id, List[EventData], ExpectedVersion, Boolean)] =
      c.WriteEvents.unapply(arg)

    def apply(
      streamId:        EventStream.Id,
      events:          List[EventData],
      expectedVersion: ExpectedVersion = ExpectedVersion.Any,
      requireMaster:   Boolean         = RequireMaster
    ): WriteEvents = c.WriteEvents(streamId, events, expectedVersion, requireMaster)

    object StreamMetadata {

      def apply(
        streamId:        EventStream.Metadata,
        data:            Content,
        expectedVersion: ExpectedVersion = ExpectedVersion.Any,
        requireMaster:   Boolean         = RequireMaster
      ): WriteEvents = c.WriteEvents.StreamMetadata(streamId, data, randomUuid, expectedVersion, requireMaster)

    }
  }

  type WriteEventsCompleted = c.WriteEventsCompleted
  val  WriteEventsCompleted = c.WriteEventsCompleted

  type DeleteStream = c.DeleteStream
  object DeleteStream {

    def unapply(arg: DeleteStream): Option[(EventStream.Id, ExpectedVersion.Existing, Boolean, Boolean)] =
      c.DeleteStream.unapply(arg)

    def apply(
      streamId:        EventStream.Id,
      expectedVersion: ExpectedVersion.Existing = ExpectedVersion.Any,
      hard:            Boolean                  = false,
      requireMaster: Boolean                    = RequireMaster
    ): DeleteStream = c.DeleteStream(streamId, expectedVersion, hard, requireMaster)
  }

  type DeleteStreamCompleted = c.DeleteStreamCompleted
  val  DeleteStreamCompleted = c.DeleteStreamCompleted

  type TransactionStart = c.TransactionStart
  object TransactionStart {

    def unapply(arg: TransactionStart): Option[(EventStream.Id, ExpectedVersion, Boolean)] =
      c.TransactionStart.unapply(arg)

    def apply(
      streamId:        EventStream.Id,
      expectedVersion: ExpectedVersion = ExpectedVersion.Any,
      requireMaster:  Boolean          = RequireMaster
    ): TransactionStart = c.TransactionStart(streamId, expectedVersion, requireMaster)
  }

  type TransactionStartCompleted = c.TransactionStartCompleted
  val  TransactionStartCompleted = c.TransactionStartCompleted

  type TransactionWrite = c.TransactionWrite
  object TransactionWrite {

    def unapply(arg: TransactionWrite): Option[(Long, List[EventData], Boolean)] =
      c.TransactionWrite.unapply(arg)

    def apply(
      transactionId:  Long,
      events:         List[EventData],
      requireMaster:  Boolean          = RequireMaster
    ): TransactionWrite = c.TransactionWrite(transactionId, events, requireMaster)
  }

  type TransactionWriteCompleted = c.TransactionWriteCompleted
  val  TransactionWriteCompleted = c.TransactionWriteCompleted

  type TransactionCommit = c.TransactionCommit
  object TransactionCommit {

    def unapply(arg: TransactionCommit): Option[(Long, Boolean)] =
      c.TransactionCommit.unapply(arg)

    def apply(
      transactionId: Long,
      requireMaster: Boolean = RequireMaster
    ): TransactionCommit = c.TransactionCommit(transactionId, requireMaster)
  }

  type TransactionCommitCompleted = c.TransactionCommitCompleted
  val  TransactionCommitCompleted = c.TransactionCommitCompleted

  type ReadEvent = c.ReadEvent
  object ReadEvent {

    def unapply(arg: ReadEvent): Option[(EventStream.Id, EventNumber, Boolean, Boolean)] =
      c.ReadEvent.unapply(arg)

    def apply(
      streamId:       EventStream.Id,
      eventNumber:    EventNumber = EventNumber.First,
      resolveLinkTos: Boolean     = ResolveLinkTos,
      requireMaster:  Boolean     = RequireMaster
    ): ReadEvent = c.ReadEvent(streamId, eventNumber, resolveLinkTos, requireMaster)

    object StreamMetadata {
      def apply(
        streamId:       EventStream.Metadata,
        eventNumber:    EventNumber = EventNumber.Last,
        resolveLinkTos: Boolean     = ResolveLinkTos,
        requireMaster:  Boolean     = RequireMaster
      ): ReadEvent = c.ReadEvent.StreamMetadata(streamId, eventNumber, resolveLinkTos, requireMaster)
    }
  }

  type ReadEventCompleted = c.ReadEventCompleted
  val  ReadEventCompleted = c.ReadEventCompleted

  type ReadStreamEvents = c.ReadStreamEvents
  object ReadStreamEvents {

    def unapply(arg: ReadStreamEvents): Option[(EventStream.Id, EventNumber, Int, ReadDirection, Boolean, Boolean)] =
      c.ReadStreamEvents.unapply(arg)

    def apply(
      streamId:       EventStream.Id,
      fromNumber:     EventNumber   = EventNumber.First,
      maxCount:       Int           = ReadBatchSize,
      direction:      ReadDirection = ReadDirection.Forward,
      resolveLinkTos: Boolean       = ResolveLinkTos,
      requireMaster:  Boolean       = RequireMaster
    ): ReadStreamEvents = c.ReadStreamEvents(streamId, fromNumber, maxCount, direction, resolveLinkTos, requireMaster)
  }

  type ReadStreamEventsCompleted = c.ReadStreamEventsCompleted
  val  ReadStreamEventsCompleted = c.ReadStreamEventsCompleted

  type ReadAllEvents = c.ReadAllEvents
  object ReadAllEvents {

    def unapply(arg: ReadAllEvents): Option[(Position, Int, ReadDirection, Boolean, Boolean)] =
      c.ReadAllEvents.unapply(arg)

    def apply(
      fromPosition: Position   = Position.First,
      maxCount: Int            = ReadBatchSize,
      direction: ReadDirection = ReadDirection.Forward,
      resolveLinkTos: Boolean  = ResolveLinkTos,
      requireMaster:  Boolean  = RequireMaster
    ): ReadAllEvents = c.ReadAllEvents(fromPosition, maxCount, direction, resolveLinkTos, requireMaster)
  }

  type ReadAllEventsCompleted = c.ReadAllEventsCompleted
  val  ReadAllEventsCompleted = c.ReadAllEventsCompleted

  object PersistentSubscription {

    import c.EventStream.Id
    import c.{PersistentSubscription => PS}
    import cs.{PersistentSubscriptionSettings => PSS}
    import PersistentSubscriptionSettings.{Default => D}

    def create(streamId: Id, groupName: String, settings: PSS): Create = Create(streamId, groupName, settings)
    def update(streamId: Id, groupName: String, settings: PSS): Update = Update(streamId, groupName, settings)
    def delete(streamId: Id, groupName: String): Delete                = Delete(streamId, groupName)

    object Create {
      def unapply(arg: Create): Option[(Id, String, PSS)]                   = PS.Create.unapply(arg)
      def apply(streamId: Id, groupName: String, settings: PSS = D): Create = PS.Create(streamId, groupName, settings)
    }

    object Update {
      def unapply(arg: Update): Option[(Id, String, PSS)]                   = PS.Update.unapply(arg)
      def apply(streamId: Id, groupName: String, settings: PSS = D): Update = PS.Update(streamId, groupName, settings)
    }

    type Create          = PS.Create
    val  CreateCompleted = PS.CreateCompleted
    type Update          = PS.Update
    val  UpdateCompleted = PS.UpdateCompleted
    type Delete          = PS.Delete
    val  Delete          = PS.Delete
    val  DeleteCompleted = PS.DeleteCompleted
    type Ack             = PS.Ack
    val  Ack             = PS.Ack
    type Nak             = PS.Nak
    val  Nak             = PS.Nak
    type Connect         = PS.Connect
    val  Connect         = PS.Connect
    type Connected       = PS.Connected
    val  Connected       = PS.Connected
    type EventAppeared   = PS.EventAppeared
    val  EventAppeared   = PS.EventAppeared

  }

  type SubscribeTo = c.SubscribeTo
  object SubscribeTo {
    def unapply(arg: SubscribeTo): Option[(EventStream, Boolean)]                         = c.SubscribeTo.unapply(arg)
    def apply(stream: EventStream, resolveLinkTos: Boolean = ResolveLinkTos): SubscribeTo = c.SubscribeTo(stream, resolveLinkTos)
  }

  type SubscribeCompleted         = c.SubscribeCompleted
  type SubscribeToAllCompleted    = c.SubscribeToAllCompleted
  val  SubscribeToAllCompleted    = c.SubscribeToAllCompleted
  type SubscribeToStreamCompleted = c.SubscribeToStreamCompleted
  val  SubscribeToStreamCompleted = c.SubscribeToStreamCompleted
  type StreamEventAppeared        = c.StreamEventAppeared
  val  StreamEventAppeared        = c.StreamEventAppeared
  val  Unsubscribe                = c.Unsubscribe
  val  Unsubscribed               = c.Unsubscribed
  val  ScavengeDatabase           = c.ScavengeDatabase
  type ScavengeDatabaseResponse   = c.ScavengeDatabaseResponse
  val  ScavengeDatabaseResponse   = c.ScavengeDatabaseResponse
  val  Authenticate               = c.Authenticate
  val  Authenticated              = c.Authenticated

  /// Misc

  type UserCredentials  = c.UserCredentials
  val  UserCredentials  = c.UserCredentials
  type WriteResult      = c.WriteResult
  val  WriteResult      = c.WriteResult
  type DeleteResult     = c.DeleteResult
  val  DeleteResult     = c.DeleteResult
  type ByteString       = c.ByteString
  val  ByteString       = c.ByteString
  type ConsumerStrategy = c.ConsumerStrategy
  val  ConsumerStrategy = c.ConsumerStrategy

  /// Exceptions

  type EsException                        = c.EsException
  type CannotEstablishConnectionException = c.CannotEstablishConnectionException
  val  CannotEstablishConnectionException = c.CannotEstablishConnectionException
  type StreamNotFoundException            = c.StreamNotFoundException
  val  StreamNotFoundException            = c.StreamNotFoundException
  type StreamDeletedException             = c.StreamDeletedException
  val  StreamDeletedException             = c.StreamDeletedException
  type AccessDeniedException              = c.AccessDeniedException
  val  AccessDeniedException              = c.AccessDeniedException
  val  InvalidTransactionException        = c.InvalidTransactionException
  type WrongExpectedVersionException      = c.WrongExpectedVersionException
  val  WrongExpectedVersionException      = c.WrongExpectedVersionException
  type ServerErrorException               = c.ServerErrorException
  val  ServerErrorException               = c.ServerErrorException
  type EventNotFoundException             = c.EventNotFoundException
  val  EventNotFoundException             = c.EventNotFoundException
  type NotAuthenticatedException          = c.NotAuthenticatedException
  val  NotAuthenticatedException          = c.NotAuthenticatedException
  type NonMetadataEventException          = c.NonMetadataEventException
  val  NonMetadataEventException          = c.NonMetadataEventException
  type OperationTimeoutException          = c.OperationTimeoutException
  val  OperationTimeoutException          = c.OperationTimeoutException
  val  ScavengeInProgressException        = c.ScavengeInProgressException
  val  ScavengeUnauthorizedException      = c.ScavengeUnauthorizedException
  type CommandNotExpectedException        = c.CommandNotExpectedException
  val  CommandNotExpectedException        = c.CommandNotExpectedException
  type RetriesLimitReachedException       = c.RetriesLimitReachedException
  val  RetriesLimitReachedException       = c.RetriesLimitReachedException
  type InvalidOperationException          = c.InvalidOperationException
  val  InvalidOperationException          = c.InvalidOperationException

  /// Errors

  type SystemError                       = c.SystemError
  type ServerError                       = c.ServerError
  val  OperationTimedOut                 = c.OperationTimedOut
  val  BadRequest                        = c.BadRequest
  val  NotAuthenticated                  = c.NotAuthenticated
  type OperationError                    = c.OperationError
  val  OperationError                    = c.OperationError
  type ReadEventError                    = c.ReadEventError
  val  ReadEventError                    = c.ReadEventError
  type ReadStreamEventsError             = c.ReadStreamEventsError
  val  ReadStreamEventsError             = c.ReadStreamEventsError
  type ReadAllEventsError                = c.ReadAllEventsError
  val  ReadAllEventsError                = c.ReadAllEventsError
  type NotHandled                        = c.NotHandled
  val  NotHandled                        = c.NotHandled
  type SubscriptionDropped               = c.SubscriptionDropped
  val  SubscriptionDropped               = c.SubscriptionDropped
  type ScavengeError                     = c.ScavengeError
  val  ScavengeError                     = c.ScavengeError
  type CreatePersistentSubscriptionError = c.CreatePersistentSubscriptionError
  val  CreatePersistentSubscriptionError = c.CreatePersistentSubscriptionError
  type UpdatePersistentSubscriptionError = c.UpdatePersistentSubscriptionError
  val  UpdatePersistentSubscriptionError = c.UpdatePersistentSubscriptionError
  type DeletePersistentSubscriptionError = c.DeletePersistentSubscriptionError
  val  DeletePersistentSubscriptionError = c.DeletePersistentSubscriptionError
}