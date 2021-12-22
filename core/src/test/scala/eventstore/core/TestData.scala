package eventstore
package core

import util.uuid.randomUuid
import settings.PersistentSubscriptionSettings

object TestData {

  val readBatchSize: Int                    = 500
  val requireMaster: Boolean                = true
  val resolveLinkTos: Boolean               = false
  val pss                                   = PersistentSubscriptionSettings()
  val streamId: EventStream.Id              = EventStream.Id("test")
  val expectedAny: ExpectedVersion.Existing = ExpectedVersion.Any
  val psGroup: String                       = "test-group"
  val hardDelete: Boolean                   = true
  val exactN: EventNumber.Exact             = EventNumber.First
  val exactP: Position.Exact                = Position.First
  val readDirection: ReadDirection          = ReadDirection.Forward
  val eventData: EventData                  = EventData("type", randomUuid, Content.Empty, Content.Empty)
  val eventRecord: EventRecord              = EventRecord(streamId, exactN, eventData)
  val indexedEvent: IndexedEvent            = IndexedEvent(eventRecord, Position.First)

  def psCreate: PersistentSubscription.Create = PersistentSubscription.Create(streamId, psGroup, pss)
  def psUpdate: PersistentSubscription.Update = PersistentSubscription.Update(streamId, psGroup, pss)
  def psDelete: PersistentSubscription.Delete = PersistentSubscription.Delete(streamId, psGroup)

  def writeEventsCompleted: WriteEventsCompleted = WriteEventsCompleted(None, None)
  def writeEvents: WriteEvents = WriteEvents(streamId, Nil, expectedAny, requireMaster)

  def deleteStream: DeleteStream = DeleteStream(streamId, expectedAny, hardDelete, requireMaster)
  def deleteStreamCompleted: DeleteStreamCompleted = DeleteStreamCompleted(None)

  def readEvent: ReadEvent = readEvent(streamId)
  def readEventCompleted: ReadEventCompleted = ReadEventCompleted(eventRecord)

  def readStreamEvents: ReadStreamEvents = readStreamEvents(streamId)
  def readStreamEventsCompleted: ReadStreamEventsCompleted = ReadStreamEventsCompleted(Nil, exactN, exactN, true, 1L, readDirection)

  def readAllEvents: ReadAllEvents = readAllEvents(exactP)
  def readAllEventsCompleted: ReadAllEventsCompleted = ReadAllEventsCompleted(Nil, exactP, exactP, readDirection)

  def scavengeDatabaseResponse: ScavengeDatabaseResponse = ScavengeDatabaseResponse(None)

  def transactionCommit: TransactionCommit = TransactionCommit(1L, requireMaster)
  def transactionCommitCompleted: TransactionCommitCompleted = TransactionCommitCompleted(1L, None, None)

  def transactionStart: TransactionStart = TransactionStart(streamId, expectedAny, requireMaster)
  def transactionStartCompleted: TransactionStartCompleted = TransactionStartCompleted(1L)

  def transactionWrite: TransactionWrite = TransactionWrite(1L, Nil, requireMaster)
  def transactionWriteCompleted: TransactionWriteCompleted = TransactionWriteCompleted(1L)

  ///

  def subscribeTo(
    stream:         EventStream,
    resolveLinkTos: Boolean    = resolveLinkTos
  ): SubscribeTo = SubscribeTo(stream, resolveLinkTos)

  def readEvent(
    streamId:       EventStream.Id,
    eventNumber:    EventNumber   = EventNumber.First,
    resolveLinkTos: Boolean       = resolveLinkTos,
    requireMaster:  Boolean       = requireMaster
  ): ReadEvent = ReadEvent(streamId, eventNumber, resolveLinkTos, requireMaster)

  def readStreamEvents(
    streamId:       EventStream.Id,
    fromNumber:     EventNumber   = EventNumber.First,
    maxCount:       Int           = readBatchSize,
    direction:      ReadDirection = ReadDirection.Forward,
    resolveLinkTos: Boolean       = resolveLinkTos,
    requireMaster:  Boolean       = requireMaster
  ): ReadStreamEvents = ReadStreamEvents(streamId, fromNumber, maxCount, direction, resolveLinkTos, requireMaster)

  def readAllEvents(
    fromPosition: Position   = Position.First,
    maxCount: Int            = readBatchSize,
    direction: ReadDirection = ReadDirection.Forward,
    resolveLinkTos: Boolean  = resolveLinkTos,
    requireMaster:  Boolean  = requireMaster
  ): ReadAllEvents = ReadAllEvents(fromPosition, maxCount, direction, resolveLinkTos, requireMaster)


}
