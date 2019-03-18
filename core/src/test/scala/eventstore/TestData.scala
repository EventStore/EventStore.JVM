package eventstore

import eventstore.util.uuid.randomUuid
import eventstore.{PersistentSubscription ⇒ Ps}

object TestData {

  val streamId: EventStream.Id              = EventStream.Id("test")
  val expectedAny: ExpectedVersion.Existing = ExpectedVersion.Any
  val requireMaster: Boolean                = true
  val psGroup: String                       = "test-group"
  val hardDelete: Boolean                   = true
  val exactN: EventNumber.Exact             = EventNumber.First
  val exactP: Position.Exact                = Position.First
  val readDirection: ReadDirection          = ReadDirection.Forward
  val eventData: EventData                  = EventData("type", randomUuid, Content.Empty, Content.Empty)
  val eventRecord: EventRecord              = EventRecord(streamId, exactN, eventData)
  val indexedEvent: IndexedEvent            = IndexedEvent(eventRecord, Position.First)

  def psCreate: Ps.Create = Ps.Create(streamId, psGroup)
  def psUpdate: Ps.Update = Ps.Update(streamId, psGroup)
  def psDelete: Ps.Delete = Ps.Delete(streamId, psGroup)

  def writeEventsCompleted: WriteEventsCompleted = WriteEventsCompleted(None, None)
  def writeEvents: WriteEvents = WriteEvents(streamId, Nil, expectedAny, requireMaster)

  def deleteStream: DeleteStream = DeleteStream(streamId, expectedAny, hardDelete, requireMaster)
  def deleteStreamCompleted: DeleteStreamCompleted = DeleteStreamCompleted(None)

  def readEvent: ReadEvent = ReadEvent(streamId)
  def readEventCompleted: ReadEventCompleted = ReadEventCompleted(eventRecord)

  def readStreamEvents: ReadStreamEvents = ReadStreamEvents(streamId)
  def readStreamEventsCompleted: ReadStreamEventsCompleted = ReadStreamEventsCompleted(Nil, exactN, exactN, true, 1L, readDirection)

  def readAllEvents: ReadAllEvents = ReadAllEvents(exactP)
  def readAllEventsCompleted: ReadAllEventsCompleted = ReadAllEventsCompleted(Nil, exactP, exactP, readDirection)

  def scavengeDatabaseResponse: ScavengeDatabaseResponse = ScavengeDatabaseResponse(None)

  def transactionCommit: TransactionCommit = TransactionCommit(1L, requireMaster)
  def transactionCommitCompleted: TransactionCommitCompleted = TransactionCommitCompleted(1L)

  def transactionStart: TransactionStart = TransactionStart(streamId, expectedAny, requireMaster)
  def transactionStartCompleted: TransactionStartCompleted = TransactionStartCompleted(1L)

  def transactionWrite: TransactionWrite = TransactionWrite(1L, Nil, requireMaster)
  def transactionWriteCompleted: TransactionWriteCompleted = TransactionWriteCompleted(1L)

}
