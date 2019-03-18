package eventstore
package akka

import eventstore.{PersistentSubscription ⇒ Ps}

object TestData {

  val streamId: EventStream.Id              = EventStream.Id("test")
  val expectedAny: ExpectedVersion.Existing = ExpectedVersion.Any
  val requireMaster: Boolean                = true
  val resolveLinkTos: Boolean               = true
  val psGroup: String                       = "test-group"
  val hardDelete: Boolean                   = true
  val exactN: EventNumber.Exact             = EventNumber.First
  val exactP: Position.Exact                = Position.First
  val readDirection: ReadDirection          = ReadDirection.Forward
  val eventData: EventData                  = EventData("type", randomUuid, Content.Empty, Content.Empty)
  val eventRecord: EventRecord              = EventRecord(streamId, exactN, eventData)
  val indexedEvent: IndexedEvent            = IndexedEvent(eventRecord, Position.First)

  def psCreate: Ps.Create = Ps.Create(streamId, psGroup)
  def psCreateCompleted: Ps.CreateCompleted.type = Ps.CreateCompleted

  def psUpdate: Ps.Update = Ps.Update(streamId, psGroup)
  def psUpdateCompleted: Ps.UpdateCompleted.type = Ps.UpdateCompleted

  def psDelete: Ps.Delete = Ps.Delete(streamId, psGroup)
  def psDeleteCompleted: Ps.DeleteCompleted.type = Ps.DeleteCompleted

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

  def subscribeTo: SubscribeTo = SubscribeTo(streamId, resolveLinkTos)
  def subscribeToStreamCompleted: SubscribeToStreamCompleted = SubscribeToStreamCompleted(1L, None)

}
