package eventstore
package j

import java.util
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.javadsl.Source
import eventstore.ExpectedVersion.Existing

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object EsConnectionImpl {
  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnectionImpl =
    new EsConnectionImpl(eventstore.EsConnection(system, settings), settings, system.dispatcher)
}

class EsConnectionImpl(
    connection:                            eventstore.EsConnection,
    settings:                              Settings,
    private implicit val executionContext: ExecutionContext
) extends EsConnection {

  def writeEvents(
    stream:          String,
    expectedVersion: ExpectedVersion,
    events:          util.Collection[EventData],
    credentials:     UserCredentials
  ) = {

    writeEvents(stream, expectedVersion, events, credentials, settings.requireMaster)
  }

  def writeEvents(
    stream:          String,
    expectedVersion: ExpectedVersion,
    events:          util.Collection[EventData],
    credentials:     UserCredentials,
    requireMaster:   Boolean
  ) = {

    val out = WriteEvents(
      streamId = EventStream.Id(stream),
      events = events.asScala.toList,
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials)).map(x => WriteResult.opt(x).orNull)
  }

  def deleteStream(stream: String, expectedVersion: ExpectedVersion.Existing, credentials: UserCredentials) = {
    deleteStream(stream, expectedVersion, hardDelete = false, credentials)
  }

  def deleteStream(stream: String, expectedVersion: Existing, hardDelete: Boolean, credentials: UserCredentials) = {
    deleteStream(stream, expectedVersion, hardDelete, credentials, settings.requireMaster)
  }

  def deleteStream(
    stream:          String,
    expectedVersion: Existing,
    hardDelete:      Boolean,
    credentials:     UserCredentials,
    requireMaster:   Boolean
  ) = {

    val out = DeleteStream(
      streamId = EventStream.Id(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any,
      hard = hardDelete,
      requireMaster = requireMaster
    )
    connection(out, Option(credentials)).map(x => x.position.map(DeleteResult.apply).orNull)
  }

  def startTransaction(stream: String, expectedVersion: ExpectedVersion, credentials: UserCredentials) = {
    startTransaction(stream, expectedVersion, credentials, settings.requireMaster)
  }

  def startTransaction(stream: String, expectedVersion: ExpectedVersion, credentials: UserCredentials, requireMaster: Boolean) = {
    val msg = TransactionStart(
      streamId = EventStream.Id(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any,
      requireMaster = requireMaster
    )
    connection.startTransaction(msg, Option(credentials)).map(new EsTransactionImpl(_))
  }

  def continueTransaction(transactionId: Long, credentials: UserCredentials) = {
    val transaction = connection.continueTransaction(transactionId, Option(credentials))
    new EsTransactionImpl(transaction)
  }

  def readEvent(
    stream:         String,
    eventNumber:    EventNumber,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    readEvent(stream, eventNumber, resolveLinkTos, credentials, settings.requireMaster)
  }

  def readEvent(
    stream:         String,
    eventNumber:    EventNumber,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials,
    requireMaster:  Boolean
  ) = {

    val out = ReadEvent(
      streamId = EventStream.Id(stream),
      eventNumber = Option(eventNumber) getOrElse EventNumber.Last,
      resolveLinkTos = resolveLinkTos,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials)).map(_.event)
  }

  def readStreamEventsForward(
    stream:         String,
    fromNumber:     EventNumber.Exact,
    count:          Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    readStreamEventsForward(stream, fromNumber, count, resolveLinkTos, credentials, settings.requireMaster)
  }

  def readStreamEventsForward(
    stream:         String,
    fromNumber:     EventNumber.Exact,
    count:          Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials,
    requireMaster:  Boolean
  ) = {

    val out = ReadStreamEvents(
      streamId = EventStream.Id(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.First,
      maxCount = count,
      direction = eventstore.ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials))
  }

  def readStreamEventsBackward(
    stream:         String,
    fromNumber:     EventNumber,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    readStreamEventsBackward(stream, fromNumber, maxCount, resolveLinkTos, credentials, settings.requireMaster)
  }

  def readStreamEventsBackward(
    stream:         String,
    fromNumber:     EventNumber,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials,
    requireMaster:  Boolean
  ) = {

    val out = ReadStreamEvents(
      streamId = EventStream.Id(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.Last,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials))
  }

  def readAllEventsForward(
    fromPosition:   Position,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    readAllEventsForward(fromPosition, maxCount, resolveLinkTos, credentials, settings.requireMaster)
  }

  def readAllEventsForward(
    fromPosition:   Position,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials,
    requireMaster:  Boolean
  ) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.First,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials))
  }

  def readAllEventsBackward(
    fromPosition:   Position,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    readAllEventsBackward(fromPosition, maxCount, resolveLinkTos, credentials, settings.requireMaster)
  }

  def readAllEventsBackward(
    fromPosition:   Position,
    maxCount:       Int,
    resolveLinkTos: Boolean,
    credentials:    UserCredentials,
    requireMaster:  Boolean
  ) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.Last,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos,
      requireMaster = requireMaster
    )

    connection(out, Option(credentials))
  }

  def subscribeToStream(
    stream:         String,
    observer:       SubscriptionObserver[Event],
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    connection.subscribeToStream(EventStream.Id(stream), observer, resolveLinkTos, Option(credentials))
  }

  def subscribeToStreamFrom(
    stream:                   String,
    observer:                 SubscriptionObserver[Event],
    fromEventNumberExclusive: java.lang.Long,
    resolveLinkTos:           Boolean,
    credentials:              UserCredentials
  ) = {

    connection.subscribeToStreamFrom(
      EventStream.Id(stream),
      observer,
      Option(fromEventNumberExclusive).map(EventNumber.Exact(_)),
      resolveLinkTos,
      Option(credentials)
    )
  }

  def subscribeToAll(
    observer:       SubscriptionObserver[IndexedEvent],
    resolveLinkTos: Boolean,
    credentials:    UserCredentials
  ) = {

    connection.subscribeToAll(observer, resolveLinkTos, Option(credentials))
  }

  def subscribeToAllFrom(
    observer:              SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Position.Exact,
    resolveLinkTos:        Boolean,
    credentials:           UserCredentials
  ) = {

    connection.subscribeToAllFrom(observer, Option(fromPositionExclusive), resolveLinkTos, Option(credentials))
  }

  def setStreamMetadata(
    stream:                    String,
    expectedMetastreamVersion: ExpectedVersion,
    metadata:                  Array[Byte],
    credentials:               UserCredentials
  ) = {

    connection.setStreamMetadata(
      EventStream.Id(stream),
      Content(metadata),
      Option(expectedMetastreamVersion) getOrElse ExpectedVersion.Any,
      Option(credentials)
    ).map(_.orNull)
  }

  def getStreamMetadataBytes(stream: String, credentials: UserCredentials) = {
    connection.getStreamMetadata(EventStream.Id(stream), Option(credentials)).map(_.value.toArray)
  }

  def streamPublisher(
    stream:                   String,
    fromEventNumberExclusive: EventNumber,
    resolveLinkTos:           Boolean,
    credentials:              UserCredentials,
    infinite:                 Boolean
  ) = {

    connection.streamPublisher(
      streamId = EventStream.Id(stream),
      fromNumberExclusive = Option(fromEventNumberExclusive),
      resolveLinkTos = resolveLinkTos,
      credentials = Option(credentials),
      infinite = infinite
    )
  }

  def streamSource(
    stream:                   String,
    fromEventNumberExclusive: EventNumber,
    resolveLinkTos:           Boolean,
    credentials:              UserCredentials,
    infinite:                 Boolean
  ): Source[Event, NotUsed] = {

    connection.streamSource(
      streamId = EventStream.Id(stream),
      fromEventNumberExclusive = Option(fromEventNumberExclusive),
      resolveLinkTos = resolveLinkTos,
      credentials = Option(credentials),
      infinite = infinite
    ).asJava

  }

  def allStreamsPublisher(
    fromPositionExclusive: Position,
    resolveLinkTos:        Boolean,
    credentials:           UserCredentials,
    infinite:              Boolean
  ) = {

    connection.allStreamsPublisher(
      resolveLinkTos = resolveLinkTos,
      fromPositionExclusive = Option(fromPositionExclusive),
      credentials = Option(credentials),
      infinite = infinite
    )
  }

  def allStreamsSource(
    fromPositionExclusive: Position,
    resolveLinkTos:        Boolean,
    credentials:           UserCredentials,
    infinite:              Boolean
  ): Source[IndexedEvent, NotUsed] = {

    connection.allStreamsSource(
      resolveLinkTos = resolveLinkTos,
      fromPositionExclusive = Option(fromPositionExclusive),
      credentials = Option(credentials),
      infinite = infinite
    ).asJava

  }

  def createPersistentSubscription(
    stream:      String,
    groupName:   String,
    settings:    PersistentSubscriptionSettings,
    credentials: UserCredentials
  ) = {

    val out = PersistentSubscription.Create(EventStream.Id(stream), groupName,
      Option(settings) getOrElse PersistentSubscriptionSettings.Default)
    connection.apply(out, Option(credentials)) map { _ => () }
  }

  def updatePersistentSubscription(
    stream:      String,
    groupName:   String,
    settings:    PersistentSubscriptionSettings,
    credentials: UserCredentials
  ) = {

    val out = PersistentSubscription.Update(EventStream.Id(stream), groupName,
      Option(settings) getOrElse PersistentSubscriptionSettings.Default)
    connection.apply(out, Option(credentials)) map { _ => () }
  }

  def deletePersistentSubscription(
    stream:      String,
    groupName:   String,
    credentials: UserCredentials
  ) = {

    val out = PersistentSubscription.Delete(EventStream.Id(stream), groupName)
    connection.apply(out, Option(credentials)) map { _ => () }
  }
}