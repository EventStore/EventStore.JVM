package eventstore.dsl

import java.util.UUID

import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.{ Done, NotUsed }
import eventstore._
import eventstore.dsl.WriteStream.WriteStreamError

import scala.concurrent.{ ExecutionContext, Future }

trait ReadStreamAs[T] extends BaseReadStream {

  protected[this] implicit val eventReader: EventReader[T]

  def subscribe(startOffset: Offset = Offset.First): ReadAllAs[T] = read(startOffset, infinite = true)

  def read(startOffset: Offset = Offset.First, infinite: Boolean = false): ReadAllAs[T] = ReadAllAs[T](doRead(startOffset, infinite))

  def readOne(offset: Offset)(implicit ec: ExecutionContext): ReadOneAs[T] = ReadOneAs[T](doReadOne(offset))

  def last()(implicit ec: ExecutionContext): ReadOneAs[T] = ReadOneAs[T](doLast())

  def first()(implicit ec: ExecutionContext): ReadOneAs[T] = ReadOneAs[T](doFirst())

  def firstOffset()(implicit ec: ExecutionContext): ReadOffset = new ReadOffset(doFirstOffset())

  def lastOffset()(implicit ec: ExecutionContext): ReadOffset = new ReadOffset(doLastOffset())
}

trait ReadStream extends BaseReadStream {

  def subscribe(startOffset: Offset = Offset.First): ReadAll = read(startOffset, infinite = true)

  def read(startOffset: Offset = Offset.First, infinite: Boolean = false): ReadAll = new ReadAll(doRead(startOffset, infinite))

  def readOne(offset: Offset)(implicit ec: ExecutionContext): ReadOne = new ReadOne(doReadOne(offset))

  def last()(implicit ec: ExecutionContext): ReadOne = new ReadOne(doLast())

  def first()(implicit ec: ExecutionContext): ReadOne = new ReadOne(doFirst())

  def firstOffset()(implicit ec: ExecutionContext): ReadOffset = new ReadOffset(doFirstOffset())

  def lastOffset()(implicit ec: ExecutionContext): ReadOffset = new ReadOffset(doLastOffset())
}

sealed trait BaseReadStream extends Stream {

  protected[this] def doRead(startOffset: Offset = Offset.First, infinite: Boolean = false): Source[Event, NotUsed] = {

    def toNumberExclusive: Option[EventNumber] = {
      if (startOffset == Offset.First) Option.empty
      else Some(EventNumber.Exact((startOffset.value - 1).toInt))
    }

    val publisher = connection.streamPublisher(
      streamId = streamId,
      fromNumberExclusive = toNumberExclusive,
      infinite = infinite
    )
    Source.fromPublisher(publisher)
  }

  protected[this] def doReadOne(offset: Offset)(implicit ec: ExecutionContext): Future[Event] = {
    readAt(EventNumber.Exact(offset.value.toInt))
  }

  protected[this] def doLast()(implicit ec: ExecutionContext): Future[Event] = {
    readAt(EventNumber.Last)
  }

  protected[this] def doFirst()(implicit ec: ExecutionContext): Future[Event] = {
    readAt(EventNumber.First)
  }

  protected[this] def doFirstOffset()(implicit ec: ExecutionContext): Future[Offset] = {
    readOffsetAt(EventNumber.First)
  }

  protected[this] def readOffsetAt(eventNumber: EventNumber)(implicit ec: ExecutionContext): Future[Offset] = {
    for {
      event <- readAt(eventNumber)
    } yield Offset(event.record.number.value.toLong)
  }

  protected[this] def readAt(eventNumber: EventNumber)(implicit ec: ExecutionContext): Future[Event] = {
    val readCommand = ReadEvent(streamId, eventNumber)
    connection(readCommand).map { e => e.event }
  }

  protected[this] def doLastOffset()(implicit ec: ExecutionContext): Future[Offset] = {
    readOffsetAt(EventNumber.Last)
  }

}

object ReadStream {

  sealed trait ReadStreamError

  case class ReadStreamFailure(ex: EsException) extends ReadStreamError

  case class UnsupportedEventFailure(event: Event, cause: Throwable) extends ReadStreamError

}

trait MetadataStream extends Stream {

  def writeMetadata[T](metadata: T)(implicit mf: MetadataWriter[T], ec: ExecutionContext): Future[Unit] = {
    connection
      .setStreamMetadata(streamId, mf.write(metadata))
      .map(_ => ())
  }

  def readMetadata()(implicit ec: ExecutionContext): ReadMetadata = {
    new ReadMetadata(connection.getStreamMetadata(streamId))
  }
}

object MetadataStream {

  sealed trait ReadMetadataError

  case class ReadMetadataFailure(ex: EsException) extends ReadMetadataError

  case class UnsupportedMetadataFailure(content: Content, cause: Throwable) extends ReadMetadataError

}

trait WriteStreamAs[T] extends BaseWriteStream {
  protected[this] implicit val eventWriter: EventWriter[T]

  def append(events: T*)(implicit ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](events = List(events: _*))
  }

  def appendSeq(events: Seq[T])(implicit ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](events = events)
  }

  def appendAt(offset: Offset, events: T*)(implicit ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](Some(offset), events)
  }

  def appendSeqAt(offset: Offset, events: Seq[T])(implicit ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](Some(offset), events)
  }
}

trait WriteStream extends BaseWriteStream {
  def append[T](events: T*)(implicit ef: EventWriter[T], ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](events = List(events: _*))
  }

  def appendSeq[T](events: Seq[T])(implicit ef: EventWriter[T], ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](events = events)
  }

  def appendAt[T](offset: Offset, events: T*)(implicit ef: EventWriter[T], ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](Some(offset), events)
  }

  def appendSeqAt[T](offset: Offset, events: Seq[T])(implicit ef: EventWriter[T], ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {
    write[T](Some(offset), events)
  }
}

object StreamImplicits {
  implicit class WriteStreamImplicit(val future: Future[Either[WriteStreamError, WriteEventsCompleted]]) extends AnyVal {

    def failOnError(implicit ec: ExecutionContext): Future[Done] = {
      future.map {
        case Left(error) => throw error.cause
        case Right(_)    => Done
      }
    }
  }

}

trait BaseWriteStream extends Stream {
  import Recovery._

  def delete(hardDelete: Boolean = false)(implicit ec: ExecutionContext): Future[Either[WriteStreamError, DeleteStreamCompleted]] = {
    connection(DeleteStream(streamId, hard = hardDelete)).map(Right(_))
      .recover(recoverAllEsExceptionForWrite)
  }

  protected[this] def write[T](startOffset: Option[Offset] = None, events: Seq[T])(implicit writer: EventWriter[T], ec: ExecutionContext): Future[Either[WriteStreamError, WriteEventsCompleted]] = {

    val expectedVersion = startOffset match {
      case None               => ExpectedVersion.Any
      case Some(Offset.First) => ExpectedVersion.NoStream
      case Some(offset)       => ExpectedVersion.Exact(offset.previous.get.value.toInt)
    }

    val writeCommand = WriteEvents(
      expectedVersion = expectedVersion,
      streamId = streamId,
      events = events.map(writer.write).toList
    )

    connection(writeCommand)
      .map(Right(_))
      .recover(recoverAllEsExceptionForWrite)
  }

}

object WriteStream {

  sealed trait WriteStreamError {
    val cause: Throwable
  }

  case class WriteStreamFailure(cause: EsException) extends WriteStreamError

  case class UnsupportedWriteFailure(event: Event, cause: Throwable) extends WriteStreamError

  object WriteStreamError {
    def unapply(arg: WriteStreamError): Option[Throwable] = {
      Some(arg match {
        case WriteStreamFailure(cause)         => cause
        case UnsupportedWriteFailure(_, cause) => cause
      })
    }
  }

}

sealed trait Stream {
  protected[this] val connection: EsConnection
  protected val streamId: EventStream.Id
}

class EsStream(val connection: EsConnection, val streamId: EventStream.Id) extends Stream with ReadStream with WriteStream with MetadataStream

class EsStreamAs[T](val connection: EsConnection, val streamId: EventStream.Id, _eventFormat: EventFormat[T])
    extends Stream with ReadStreamAs[T] with WriteStreamAs[T] with MetadataStream {
  protected[this] implicit val eventReader = _eventFormat
  protected[this] implicit val eventWriter = _eventFormat
}

class EsStreamCategory(val connection: EsConnection, val category: String) extends BaseStreamCategory {
  def withId(id: UUID): EsStream = {
    withId(id.toString)
  }

  def withId(id: String): EsStream = {
    require(Option(id).exists(_.nonEmpty), "Id cannot be empty")
    new EsStream(connection, EventStream.Id(s"$category-$id"))
  }
}

class EsStreamCategoryAs[T](val connection: EsConnection, val category: String)(implicit eventFormat: EventFormat[T]) extends BaseStreamCategory {
  def withId(id: UUID): EsStreamAs[T] = {
    withId(id.toString)
  }

  def withId(id: String): EsStreamAs[T] = {
    require(Option(id).exists(_.nonEmpty), "Id cannot be empty")
    new EsStreamAs[T](connection, EventStream.Id(s"$category-$id"), eventFormat)
  }
}

trait BaseStreamCategory {
  protected[this] val connection: EsConnection
  protected[this] val category: String

  require(!category.contains("-"), "Category name cannot contain '-'")
  require(!category.contains("$"), "Category name cannot contain the '$' character")

  def delete(hardDelete: Boolean = false)(implicit executionContext: ExecutionContext, materializer: Materializer): Future[Unit] = {

    def extractCategoryName = (str: String) => str.split("-").headOption match {
      case Some(categoryName) => categoryName
      case None               => ""
    }

    val allStreamSource: Source[String, NotUsed] = Source.fromPublisher(
      connection.allStreamsPublisher(fromPositionExclusive = Some(Position.Last), infinite = false)
    )
      .map(_.event.streamId.streamId)
      .filter(streamName => extractCategoryName(streamName).equals(category))
      .fold[Set[String]](Set())((streams, stream) => streams + stream)
      .mapConcat(identity)

    allStreamSource.mapAsync(1)(streamId => connection(DeleteStream(EventStream.Id(streamId), hard = hardDelete)))
      .runWith(Sink.ignore)
      .map(_ => ())
  }
}