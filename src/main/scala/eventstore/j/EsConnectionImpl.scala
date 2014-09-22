package eventstore
package j

import java.util
import eventstore.ExpectedVersion.Existing
import scala.collection.JavaConverters._
import akka.actor.ActorSystem

object EsConnectionImpl {
  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnectionImpl =
    new EsConnectionImpl(eventstore.EsConnection(system, settings))
}

class EsConnectionImpl(connection: eventstore.EsConnection) extends EsConnection {
  import scala.concurrent.ExecutionContext.Implicits.global

  def writeEvents(
    stream: String,
    expectedVersion: ExpectedVersion,
    events: util.Collection[EventData],
    credentials: UserCredentials) = {

    val out = WriteEvents(
      streamId = EventStream.Id(stream),
      events = events.asScala.toList,
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any)

    connection.future(out, Option(credentials)).map(_ => ())
  }

  def deleteStream(stream: String, expectedVersion: ExpectedVersion.Existing, credentials: UserCredentials) = {
    deleteStream(stream, expectedVersion, hardDelete = false, credentials)
  }

  def deleteStream(stream: String, expectedVersion: Existing, hardDelete: Boolean, credentials: UserCredentials) = {
    val out = DeleteStream(
      streamId = EventStream.Id(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any,
      hard = hardDelete)
    connection.future(out, Option(credentials)).map(_ => ())
  }

  def startTransaction(stream: String, expectedVersion: ExpectedVersion, credentials: UserCredentials) = {
    val msg = TransactionStart(
      streamId = EventStream.Id(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any)
    connection.startTransaction(msg, Option(credentials)).map(new EsTransactionImpl(_))
  }

  def continueTransaction(transactionId: Long, credentials: UserCredentials) = {
    val transaction = connection.continueTransaction(transactionId, Option(credentials))
    new EsTransactionImpl(transaction)
  }

  def readEvent(
    stream: String,
    eventNumber: EventNumber,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadEvent(
      streamId = EventStream.Id(stream),
      eventNumber = Option(eventNumber) getOrElse EventNumber.Last,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(credentials)).map(_.event)
  }

  def readStreamEventsForward(
    stream: String,
    fromNumber: EventNumber.Exact,
    count: Int,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadStreamEvents(
      streamId = EventStream.Id(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.First,
      maxCount = count,
      direction = eventstore.ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(credentials))
  }

  def readStreamEventsBackward(
    stream: String,
    fromNumber: EventNumber,
    maxCount: Int,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadStreamEvents(
      streamId = EventStream.Id(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.Last,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(credentials))
  }

  def readAllEventsForward(
    fromPosition: Position,
    maxCount: Int,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.First,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(credentials))
  }

  def readAllEventsBackward(
    fromPosition: Position,
    maxCount: Int,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.Last,
      maxCount = maxCount,
      direction = eventstore.ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(credentials))
  }

  def subscribeToStream(
    stream: String,
    observer: SubscriptionObserver[Event],
    resolveLinkTos: Boolean,
    credentials: UserCredentials) =
    connection.subscribeToStream(EventStream.Id(stream), observer, resolveLinkTos, Option(credentials))

  def subscribeToStreamFrom(
    stream: String,
    observer: SubscriptionObserver[Event],
    fromEventNumberExclusive: java.lang.Integer,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = connection.subscribeToStreamFrom(
    EventStream.Id(stream),
    observer,
    Option(fromEventNumberExclusive).map(EventNumber.Exact(_)),
    resolveLinkTos,
    Option(credentials))

  def subscribeToAll(
    observer: SubscriptionObserver[IndexedEvent],
    resolveLinkTos: Boolean,
    credentials: UserCredentials) =
    connection.subscribeToAll(observer, resolveLinkTos, Option(credentials))

  def subscribeToAllFrom(
    observer: SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Position.Exact,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) =
    connection.subscribeToAllFrom(observer, Option(fromPositionExclusive), resolveLinkTos, Option(credentials))

  def setStreamMetadata(
    stream: String,
    expectedMetastreamVersion: ExpectedVersion,
    metadata: Array[Byte],
    credentials: UserCredentials) = {
    connection.setStreamMetadata(
      EventStream.Id(stream),
      Content(metadata),
      Option(expectedMetastreamVersion) getOrElse ExpectedVersion.Any,
      Option(credentials))
  }

  def getStreamMetadataBytes(stream: String, credentials: UserCredentials) = {
    connection.getStreamMetadata(EventStream.Id(stream), Option(credentials)).map(_.value.toArray)
  }
}