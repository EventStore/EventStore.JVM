package eventstore
package j

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem

object EsConnectionImpl {
  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnectionImpl =
    new EsConnectionImpl(eventstore.EsConnection(system, settings))
}

class EsConnectionImpl(connection: eventstore.EsConnection) extends EsConnection {

  def writeEvents(
    stream: String,
    expectedVersion: ExpectedVersion,
    events: util.Collection[EventData],
    credentials: UserCredentials) = {

    val out = WriteEvents(
      streamId = EventStream(stream),
      events = events.asScala.toList,
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any)

    connection.future(out, Option(credentials)).map(_ => ())
  }

  def deleteStream(stream: String, expectedVersion: ExpectedVersion.Existing, credentials: UserCredentials) = {
    val out = DeleteStream(
      streamId = EventStream(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any)
    connection.future(out, Option(credentials)).map(_ => ())
  }

  def readEvent(
    stream: String,
    eventNumber: EventNumber,
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = {

    val out = ReadEvent(
      streamId = EventStream(stream),
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
      streamId = EventStream(stream),
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
      streamId = EventStream(stream),
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
    connection.subscribeToStream(EventStream(stream), observer, resolveLinkTos, Option(credentials))

  def subscribeToStreamFrom(
    stream: String,
    observer: SubscriptionObserver[Event],
    fromEventNumberExclusive: Int, // TODO how to include first event
    resolveLinkTos: Boolean,
    credentials: UserCredentials) = connection.subscribeToStreamFrom(
    EventStream(stream),
    observer,
    Some(EventNumber(fromEventNumberExclusive)),
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
}