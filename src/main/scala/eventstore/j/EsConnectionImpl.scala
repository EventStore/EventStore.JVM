package eventstore
package j

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class EsConnectionImpl(connection: eventstore.EsConnection) extends EsConnection {
  private def expectedVersionOrAny(x: ExpectedVersion): ExpectedVersion = Option(x) getOrElse ExpectedVersion.Any

  def writeEvents(
    stream: String,
    expectedVersion: ExpectedVersion,
    events: util.Collection[EventData],
    userCredentials: UserCredentials) = {

    val out = WriteEvents(
      streamId = EventStream(stream),
      events = events.asScala.toList,
      expectedVersion = expectedVersionOrAny(expectedVersion))

    connection.future(out, Option(userCredentials)).map(_ => ())
  }

  def deleteStream(stream: String, expectedVersion: ExpectedVersion.Existing, userCredentials: UserCredentials) = {
    val out = DeleteStream(
      streamId = EventStream(stream),
      expectedVersion = Option(expectedVersion) getOrElse ExpectedVersion.Any)
    connection.future(out, Option(userCredentials)).map(_ => ())
  }

  // TODO write UnitTests

  def readEvent(
    stream: String,
    eventNumber: EventNumber,
    resolveLinkTos: Boolean,
    userCredentials: UserCredentials) = {

    val out = ReadEvent(
      streamId = EventStream(stream),
      eventNumber = Option(eventNumber) getOrElse EventNumber.Last,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(userCredentials)).map(_.event)
  }

  def readStreamEventsForward(
    stream: String,
    fromNumber: EventNumber,
    count: Int,
    resolveLinkTos: Boolean,
    userCredentials: UserCredentials) = {

    val out = ReadStreamEvents(
      streamId = EventStream(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.First, // TODO write UnitTests
      maxCount = count,
      direction = ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(userCredentials))
  }

  def readStreamEventsBackward(
    stream: String,
    fromNumber: EventNumber,
    count: Int,
    resolveLinkTos: Boolean,
    userCredentials: UserCredentials) = {

    val out = ReadStreamEvents(
      streamId = EventStream(stream),
      fromNumber = Option(fromNumber) getOrElse EventNumber.Last, // TODO write UnitTests
      maxCount = count,
      direction = ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(userCredentials))
  }

  def readAllEventsForward(
    fromPosition: Position,
    maxCount: Int,
    resolveLinkTos: Boolean,
    userCredentials: UserCredentials) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.First,
      maxCount = maxCount,
      direction = ReadDirection.Forward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(userCredentials))
  }

  def readAllEventsBackward(
    fromPosition: Position,
    maxCount: Int,
    resolveLinkTos: Boolean,
    userCredentials: UserCredentials) = {

    val out = ReadAllEvents(
      fromPosition = Option(fromPosition) getOrElse Position.Last,
      maxCount = maxCount,
      direction = ReadDirection.Backward,
      resolveLinkTos = resolveLinkTos)

    connection.future(out, Option(userCredentials))
  }
}
