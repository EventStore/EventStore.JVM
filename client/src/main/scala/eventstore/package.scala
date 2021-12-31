package object eventstore {

  import eventstore.{compat, core => c}
  import eventstore.core.{settings => cs}

  private[eventstore] def randomUuid: Uuid = c.util.uuid.randomUuid

  /// ************************************** Core ************************************** ///

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
  val  PersistentSubscriptionSettings = compat.PersistentSubscriptionSettings

  /// Messages

  type ClassTags[O, I]            = c.ClassTags[O, I]
  val  ClassTags                  = c.ClassTags
  type In                         = c.In
  type Out                        = c.Out
  type OutLike                    = c.OutLike
  type WithCredentials            = c.WithCredentials
  val  Ping                       = c.Ping
  val  Pong                       = c.Pong
  type IdentifyClient             = c.IdentifyClient
  val  IdentifyClient             = c.IdentifyClient
  val  ClientIdentified           = c.ClientIdentified
  val  WithCredentials            = c.WithCredentials
  type WriteEvents                = c.WriteEvents
  val  WriteEvents                = compat.WriteEvents
  type WriteEventsCompleted       = c.WriteEventsCompleted
  val  WriteEventsCompleted       = c.WriteEventsCompleted
  type DeleteStream               = c.DeleteStream
  val  DeleteStream               = compat.DeleteStream
  type DeleteStreamCompleted      = c.DeleteStreamCompleted
  val  DeleteStreamCompleted      = c.DeleteStreamCompleted
  type TransactionStart           = c.TransactionStart
  val  TransactionStart           = compat.TransactionStart
  type TransactionStartCompleted  = c.TransactionStartCompleted
  val  TransactionStartCompleted  = c.TransactionStartCompleted
  type TransactionWrite           = c.TransactionWrite
  val  TransactionWrite           = compat.TransactionWrite
  type TransactionWriteCompleted  = c.TransactionWriteCompleted
  val  TransactionWriteCompleted  = c.TransactionWriteCompleted
  type TransactionCommit          = c.TransactionCommit
  val  TransactionCommit          = compat.TransactionCommit
  type TransactionCommitCompleted = c.TransactionCommitCompleted
  val  TransactionCommitCompleted = c.TransactionCommitCompleted
  type ReadEvent                  = c.ReadEvent
  val  ReadEvent                  = compat.ReadEvent
  type ReadEventCompleted         = c.ReadEventCompleted
  val  ReadEventCompleted         = c.ReadEventCompleted
  type ReadStreamEvents           = c.ReadStreamEvents
  val  ReadStreamEvents           = compat.ReadStreamEvents
  type ReadStreamEventsCompleted  = c.ReadStreamEventsCompleted
  val  ReadStreamEventsCompleted  = c.ReadStreamEventsCompleted
  type ReadAllEvents              = c.ReadAllEvents
  val  ReadAllEvents              = compat.ReadAllEvents
  type ReadAllEventsCompleted     = c.ReadAllEventsCompleted
  val  ReadAllEventsCompleted     = c.ReadAllEventsCompleted
  val  PersistentSubscription     = compat.PersistentSubscription
  type SubscribeTo                = c.SubscribeTo
  val  SubscribeTo                = compat.SubscribeTo
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