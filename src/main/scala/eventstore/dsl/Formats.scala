package eventstore.dsl

import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import eventstore.dsl.MetadataStream.{ ReadMetadataError, ReadMetadataFailure, UnsupportedMetadataFailure }
import eventstore.dsl.ReadStream.{ ReadStreamError, ReadStreamFailure, UnsupportedEventFailure }
import eventstore.dsl.WriteStream.{ WriteStreamError, WriteStreamFailure }
import eventstore.{ Content, EsException, Event, EventData, EventNotFoundException, NonMetadataEventException, StreamDeletedException, StreamNotFoundException }
import Recovery._
import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait EventFormat[T] extends EventReader[T] with EventWriter[T]

@implicitNotFound("No member of type class EventReader in scope for ${T}")
trait EventReader[T] {
  def read(event: Event): EsEvent[T]
  def supports(event: Event): Boolean
}

@implicitNotFound("No member of type class EventWriter in scope for ${T}")
trait EventWriter[T] {
  def write(data: T): EventData

  def eventType(data: T): String

  def eventId(data: T): UUID = {
    val _ = data
    UUID.randomUUID()
  }
}

trait MetadataFormat[M] extends MetadataReader[M] with MetadataWriter[M]

@implicitNotFound("No member of type class MetadataWriter in scope for ${M}")
trait MetadataWriter[M] {
  def write(metadata: M): Content
}

@implicitNotFound("No member of type class MetadataReader in scope for ${M}")
trait MetadataReader[M] {
  def read(content: Content): Option[M]
}

case class EsEvent[T](source: Event, payload: T) {
  def offset: Offset = Offset(source.record.number.value.toLong)
}

class ReadAllAs[T](private val readAll: ReadAll)(implicit reader: EventReader[T]) {
  def filter(matcher: (Event) => Boolean): Source[(Offset, Either[UnsupportedEventFailure, T]), NotUsed] = readAll.filter[T](matcher)
  def getOnly: Source[(Offset, T), NotUsed] = readAll.onlyAs[T]
  def get: Source[(Offset, Either[UnsupportedEventFailure, T]), NotUsed] = readAll.as[T]
  def raw: Source[(Offset, Event), NotUsed] = readAll.raw
}

object ReadAllAs {
  def apply[T](source: Source[Event, NotUsed])(implicit reader: EventReader[T]): ReadAllAs[T] = new ReadAllAs[T](new ReadAll(source))
}

class ReadAll(private val source: Source[Event, NotUsed]) extends Read {
  def filter[T](matcher: (Event) => Boolean)(implicit reader: EventReader[T]): Source[(Offset, Either[UnsupportedEventFailure, T]), NotUsed] = {
    source
      .filter(matcher)
      .map(readAs[T])
  }

  def raw: Source[(Offset, Event), NotUsed] = {
    import Read._
    source
      .map(event => event.offset -> event)
  }

  def onlyAs[T](implicit reader: EventReader[T]): Source[(Offset, T), NotUsed] = {
    as[T]
      .collect { case (offset, Right(event)) => offset -> event }
  }

  def as[T](implicit reader: EventReader[T]): Source[(Offset, Either[UnsupportedEventFailure, T]), NotUsed] = {
    source.map(readAs[T])
  }
}

class ReadOneAs[T](private val readOne: ReadOne)(implicit reader: EventReader[T]) {
  def raw(): Future[Either[ReadStreamError, Event]] = readOne.raw

  def get(): Future[T] = {
    readOne.as[T]
  }

  def getOpt(): Future[Option[T]] = {
    readOne.asOpt[T]
  }
}

object ReadOneAs {
  def apply[T](future: Future[Event])(implicit reader: EventReader[T], executionContext: ExecutionContext): ReadOneAs[T] = new ReadOneAs[T](new ReadOne(future))
}

class ReadOne(private val future: Future[Event])(implicit executionContext: ExecutionContext) extends Read {

  def raw: Future[Either[ReadStreamError, Event]] = future.map(Right(_)).recover(recoverAllEsExceptionForRead)

  def as[T](implicit reader: EventReader[T]): Future[T] = asOpt[T].map(_.get)

  def asOpt[T](implicit reader: EventReader[T]): Future[Option[T]] = {
    future
      .map(readAs[T])
      .map {
        case (_, Right(payload)) => Some(payload)
        case (_, Left(_))        => None
      }
      .recover(recoverWhenStreamNotExist[T])
  }
}

trait Read {

  private[dsl] def readAs[T](event: Event)(implicit reader: EventReader[T]): (Offset, Either[UnsupportedEventFailure, T]) = {
    import Read._
    event.offset -> read(event).right.map(_.payload)
  }

  private[dsl] def read[T](event: Event)(implicit reader: EventReader[T]): Either[UnsupportedEventFailure, EsEvent[T]] = {
    if (reader.supports(event)) {
      Right(performRead(event))
    } else {
      Left(UnsupportedEventFailure(event, new IllegalArgumentException(s"EventReader don't support eventType : ${event.data.eventType}")))
    }
  }

  private def performRead[T](event: Event)(implicit reader: EventReader[T]): EsEvent[T] = {
    Try(reader.read(event)) match {
      case Success(esEvent) => esEvent
      case Failure(error)   => throw error
    }
  }
}

object Read {

  implicit class PimpGetEventStoreEvent(val event: Event) extends AnyVal {
    def offset = Offset(event.record.number.value.toLong)
  }

}

class ReadOffset(private val future: Future[Offset])(implicit executionContext: ExecutionContext) {

  /**
   * Return the offset of the stream
   *
   * if the stream is a projection (with link event) you will get the link offset
   *
   * @return Successful Future with the offset value if the stream is valid
   *         Failed Future if the stream is not valid
   */
  def get: Future[Offset] = future

  def getAsLong: Future[Long] = future.map(_.value)

  def getOptAsLong: Future[Option[Long]] = getOpt.map {
    case Some(offset) => Some(offset.value)
    case None         => None
  }

  /**
   * Return an option offset of the stream
   *
   * if the stream is a stream which contains linkedEvent, we only returning
   *
   * @return Successful Future with the offset value if the stream is valid
   *         Failed Future if the stream is not valid
   */
  def getOpt: Future[Option[Offset]] = {
    future.map(Some(_)).recover(recoverWhenStreamNotExist[Offset])
  }
}

class ReadMetadata(private val future: Future[Content])(implicit executionContext: ExecutionContext) {

  def as[M](implicit ef: MetadataReader[M]): Future[Either[ReadMetadataError, M]] = {
    future.map(content => read(content).right.map(_.get))
      .recover(recoverAllEsException)
  }

  private def recoverAllEsException[M]: PartialFunction[Throwable, Either[ReadMetadataError, M]] = {
    case ex: EsException => Left(ReadMetadataFailure(ex))
  }

  def asOpt[M](implicit ef: MetadataReader[M]): Future[Option[M]] = {
    future.map(content => read(content) match {
      case Right(optMetadata) => optMetadata
      case Left(_)            => None
    })
      .recover(
        recoverWhenStreamNotExist[M].orElse {
          case _: NonMetadataEventException => None
        }
      )
  }

  private def read[M](content: Content)(implicit reader: MetadataReader[M]): Either[UnsupportedMetadataFailure, Option[M]] = {
    Try(reader.read(content)) match {
      case Success(optMetadata) => Right(optMetadata)
      case Failure(error)       => Left(UnsupportedMetadataFailure(content, error))
    }
  }
}

object Recovery {
  private[dsl] def recoverWhenStreamNotExist[T]: PartialFunction[Throwable, Option[T]] = {
    case _: StreamNotFoundException => None
    case _: StreamDeletedException  => None
    case _: EventNotFoundException  => None
  }

  private[dsl] def recoverAllEsExceptionForRead[T]: PartialFunction[Throwable, Either[ReadStreamError, T]] = {
    case ex: EsException => Left(ReadStreamFailure(ex))
  }

  private[dsl] def recoverAllEsExceptionForWrite[T]: PartialFunction[Throwable, Either[WriteStreamError, T]] = {
    case ex: EsException => Left(WriteStreamFailure(ex))
  }

  private[dsl] def recoverAllEsExceptionForReadMetadata[T]: PartialFunction[Throwable, Either[ReadMetadataError, T]] = {
    case ex: EsException => Left(ReadMetadataFailure(ex))
  }
}
