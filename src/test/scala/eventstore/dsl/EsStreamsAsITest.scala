package eventstore.dsl

import java.util.UUID

import akka.Done
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.{ Sink, Source }
import eventstore.dsl.EsStreamsAsSpec.jsonFormat1
import eventstore.dsl.supports.SprayJsonFormatsExtension._
import eventstore.dsl.supports.{ SprayJsonEventFormats, SprayJsonMetadataFormats }
import eventstore.{ EventData, _ }
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

sealed trait Book extends Product
object Book {
  val MapJsonFormat = Map(
    classOf[SciFiBook].getSimpleName -> SciFiBook.JsonFormat,
    classOf[FinanceBook].getSimpleName -> FinanceBook.JsonFormat,
    classOf[GodBook].getSimpleName -> GodBook.JsonFormat
  )
}

case class SciFiBook(id: UUID = UUID.randomUUID(), name: String, author: String = "") extends Book

object SciFiBook {
  implicit val JsonFormat = jsonFormat3(SciFiBook.apply)
}

case class FinanceBook(id: UUID = UUID.randomUUID(), name: String, author: String = "") extends Book

object FinanceBook {
  implicit val JsonFormat = jsonFormat3(FinanceBook.apply)
}

case class GodBook(id: UUID = UUID.randomUUID(), name: String) extends Book

object GodBook {
  implicit val JsonFormat = jsonFormat2(GodBook.apply)
}

case class CustomBookMetadata(text: String)

object CustomBookMetadata {
  implicit val JsonFormat = jsonFormat1(CustomBookMetadata.apply)
}

object EsStreamsAsSpec extends DefaultJsonProtocol {

  implicit val sciFiBookEventFormat = SprayJsonEventFormats.format[SciFiBook]

  implicit val financeBookEventFormat = SprayJsonEventFormats.format[FinanceBook]

  implicit val godBookEventFormat = SprayJsonEventFormats.format[GodBook]

  implicit val bookEventFormat = SprayJsonEventFormats.adtFormat[Book](Book.MapJsonFormat)

  implicit val customMetadataMetaFormat = SprayJsonMetadataFormats.format[CustomBookMetadata]
}

class EsStreamsAsSpec extends TestConnection {

  import EsConnectionExtension._
  import EsStreamsAsSpec._
  import TestScope._
  import StreamImplicits._
  implicit val materializer = ActorMaterializer()

  implicit val timeout = 10 seconds

  "Read Event Store" should {

    "get only with supported event" in new TestScope {
      val scifisBooks = List(SciFiBook(name = "joe"), SciFiBook(name = "brandon"), SciFiBook(name = "steve"), SciFiBook(name = "test"))
      val godBooks = List(GodBook(name = "Root"))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = SciFiBook.getClass.getSimpleName, jsonData = scifisBooks.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = GodBook.getClass.getSimpleName, jsonData = godBooks.map(_.toJson.asJsObject))
        events <- connection.getStreamAs[SciFiBook](streamId).read().getOnly.future
      } yield events.map { case (_, m) => m }

      readedEvents.await_(timeout) should beEqualTo(scifisBooks)
    }

    "get only with unsupported event" in new TestScope {
      val members = List(GodBook(name = "joe"), GodBook(name = "brandon"), GodBook(name = "steve"), GodBook(name = "test"))
      val jsonData = members.map(_.toJson.asJsObject)

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = SciFiBook.getClass.getSimpleName, jsonData = jsonData)
        events <- connection.getStreamAs[GodBook](streamId).read().getOnly.future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.await_(timeout) should beEmpty
    }

    "filter with supported event (polymorphism)" in new TestScope {
      val godBooks = List(GodBook(name = "joe"), GodBook(name = "brandon"), GodBook(name = "steve"), GodBook(name = "test"))
      val financeBooks: List[FinanceBook] = List(FinanceBook(name = "Root"))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = GodBook.getClass.getSimpleName, jsonData = godBooks.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = FinanceBook.getClass.getSimpleName, jsonData = financeBooks.map(_.toJson.asJsObject))
        events <- connection.getStreamAs[Book](streamId).read().get.map { case (offset, either) => offset -> either.right.get }.future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.await_(timeout) should beEqualTo(godBooks ++ financeBooks)
    }

    "filter with not supported event" in new TestScope {
      val godBooks = List(GodBook(name = "joe"), GodBook(name = "brandon"), GodBook(name = "steve"), GodBook(name = "test"))
      val jsonBuddy = JsObject(Map("id" -> JsString(UUID.randomUUID().toString), "buddyName" -> JsString("My Buddy")))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = GodBook.getClass.getSimpleName, jsonData = godBooks.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = "Buddy", jsonData = Seq(jsonBuddy))
        events <- connection.getStreamAs[Book](streamId).read().getOnly.future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.await_(timeout) should beEqualTo(godBooks)
    }

    "read last event on stream not found" in new TestScope {
      val lastEvent = connection.getStreamAs[Book](streamId).last().getOpt()
      lastEvent.await_(timeout) should beNone
    }

    "read first event on stream not found" in new TestScope {
      val firstEvent = connection.getStreamAs[Book](streamId).first().getOpt()
      firstEvent.await_(timeout) should beNone
    }

    "read first event on a new stream" in new TestScope {
      val book = GodBook(name = "test")

      val readedElement = for {
        _ <- append(streamId, eventType = GodBook.getClass.getSimpleName, jsonData = book.toJson.asJsObject)
        firstElement <- connection.getStreamAs[GodBook](streamId).readOne(Offset.First).getOpt()
      } yield firstElement

      readedElement.await_(timeout) should beSome(book)
    }

    "read last event on empty stream" in new TestScope {
      val readedElement = for {
        _ <- append(streamId, eventType = GodBook.getClass.getSimpleName, jsonData = GodBook(name = "test").toJson.asJsObject)
        _ <- delete(streamId)
        lastElement <- connection.getStreamAs[GodBook](streamId).last().getOpt()
      } yield lastElement

      readedElement.await_(timeout) should beNone
    }

    "read last optional event not supported on a stream" in new TestScope {
      val readedElement = for {
        _ <- append(streamId, eventType = FinanceBook.getClass.getSimpleName, jsonData = FinanceBook(name = "test").toJson.asJsObject)
        lastElement <- connection.getStreamAs[GodBook](streamId).last().getOpt()
      } yield lastElement

      readedElement.await_(timeout) should beNone
    }

    "read last event on a stream" in new TestScope {
      val book = GodBook(name = "test")

      val readedElement = for {
        _ <- append(streamId, eventType = GodBook.getClass.getSimpleName, jsonData = book.toJson.asJsObject)
        lastElement <- connection.getStreamAs[GodBook](streamId).last().getOpt()
      } yield lastElement

      readedElement.await_(timeout) should beSome(book)
    }

    "first offset of a new stream" in new TestScope {
      val readedOffset = for {
        _ <- append(newStreamId, eventType = GodBook.getClass.getSimpleName, jsonData = GodBook(name = "test").toJson.asJsObject)
        offset <- connection.getStreamAs[GodBook](streamId).firstOffset().get
      } yield offset

      readedOffset.map(_.value).await_(timeout) should be_>=(0L)
    }

    "first offset of a empty Stream" in new TestScope {
      val readedOffset = for {
        _ <- delete(streamId)
        offset <- connection.getStream(streamId).firstOffset().getOptAsLong
      } yield offset

      readedOffset.await_(timeout) should beNone
    }

    "last offset of a stream" in new TestScope {
      val readedOffset = for {
        _ <- append(streamId, eventType = GodBook.getClass.getSimpleName, jsonData = GodBook(name = "test").toJson.asJsObject)
        offset <- connection.getStreamAs[GodBook](streamId).lastOffset().get
      } yield offset

      readedOffset.map(_.value).await_(timeout) should be_>=(1L)
    }

    "last offset of a empty stream" in new TestScope {
      val readedOffset = for {
        _ <- delete(streamId)
        offset <- connection.getStreamAs[GodBook](streamId).lastOffset().getOptAsLong
      } yield offset

      readedOffset.await_(timeout) should beNone
    }

    "last offset as long of a stream not found" in new TestScope {
      val lastOffset = connection.getStreamAs[GodBook](streamId).lastOffset().getOptAsLong
      lastOffset.await_(timeout) should beNone
    }

    "last offset of a stream not found" in new TestScope {
      val lastOffset = connection.getStreamAs[GodBook](streamId).lastOffset().getOpt
      lastOffset.await_(timeout) should beNone
    }

    "last offset of a stream throw failure" in new TestScope {
      val failure = connection.getStreamAs[GodBook](streamId).lastOffset().get.failed
      failure.await_(timeout) must haveClass[StreamNotFoundException] //must matchA
    }

    "last offset as long of a stream throw failure" in new TestScope {
      val failure = connection.getStreamAs[GodBook](streamId).lastOffset().getAsLong.failed
      failure.await_(timeout) must haveClass[StreamNotFoundException] //must matchA
    }

    "last element of a stream" in new TestScope {
      val book = GodBook(name = "test")
      val readedElement = for {
        _ <- append(streamId, eventType = GodBook.getClass.getSimpleName, jsonData = book.toJson.asJsObject)
        last <- connection.getStreamAs[GodBook](streamId).last().get()
      } yield last

      readedElement.await_(timeout) should be(book)
    }

    "first element of a new stream" in new TestScope {
      val book = GodBook(name = "test")

      val readedElement = for {
        _ <- append(newStreamId, eventType = GodBook.getClass.getSimpleName, jsonData = book.toJson.asJsObject)
        first <- connection.getStreamAs[GodBook](streamId).first().get()
      } yield first

      readedElement.await_(timeout) should be(book)
    }
  }

  "Write Event Store" should {

    "read metadata in a empty stream" in new TestScope {
      val metadata = CustomBookMetadata("Custom metadata")

      val readedMetadata = for {
        result <- connection.getStreamAs[Book](streamId).readMetadata.asOpt[CustomBookMetadata]
      } yield result

      readedMetadata.await_(timeout) should beNone
    }

    "append metadata in a empty stream" in new TestScope {
      val metadata = CustomBookMetadata("Custom metadata")

      val readedMetadata = for {
        _ <- connection.getStreamAs[Book](streamId).writeMetadata(metadata)
        result <- connection.getStreamAs[Book](streamId).readMetadata.as[CustomBookMetadata]
      } yield result

      readedMetadata.await_(timeout) should beRight(metadata)
    }

    "last element in a new stream" in new TestScope {
      val secondBook = GodBook(name = "Second")

      val last = for {
        _ <- connection.getStreamAs[GodBook](streamId).append(GodBook(name = "First"), secondBook)
        result <- connection.getStreamAs[GodBook](streamId).last().get()
      } yield result

      last.await_(timeout) should beEqualTo(secondBook)
    }

    "append one element with fail on error" in new TestScope {
      val book = GodBook(name = "joe")
      val error = connection.getStreamAs[Book](streamId).append(book).failOnError

      error.await_(timeout) should beEqualTo(Done)
    }

    "append one element" in new TestScope {
      val append = connection.getStreamAs[Book](streamId).append(GodBook(name = "joe"))
      append await_ (timeout) should beRight(haveClass[WriteEventsCompleted])
    }

    "append multiple elements of same type" in new TestScope {
      val books = List(GodBook(name = "joe"), GodBook(name = "brandon"), GodBook(name = "steve"), GodBook(name = "test"))
      val append = connection.getStreamAs[GodBook](streamId).appendSeq(books).failOnError
      append.await_(timeout) should beEqualTo(Done)
    }

    "append multiple with " in new TestScope {
      val books = List(GodBook(name = "joe"), SciFiBook(name = "brandon"), FinanceBook(name = "steve"), GodBook(name = "test"))

      val append = connection.getStreamAs[Book](streamId).appendSeq(books).failOnError
      append.await_(timeout) should beEqualTo(Done)
    }
  }

  abstract class TestScope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)

    def delete(streamId: EventStream.Id): Future[DeleteStreamCompleted] = {
      connection(DeleteStream(streamId))
    }

    def append(streamId: EventStream.Id, eventType: String, jsonData: JsObject): Future[WriteEventsCompleted] = {
      append(streamId, eventType, Seq(jsonData))
    }

    def append(streamId: EventStream.Id, eventType: String, jsonData: Seq[JsObject]): Future[WriteEventsCompleted] = {
      val eventData: List[EventData] = jsonData.map(json => EventData(eventType = eventType.replace("$", ""), data = Content.Json(json.toString()))).toList

      val writeCommand = WriteEvents(
        streamId = streamId,
        events = eventData
      )

      connection(writeCommand)
    }
  }

  object TestScope {
    val MaxAllowedSeqSize = 10000

    implicit class PimpedSource[S, T](source: Source[S, T]) {
      def future(implicit mat: Materializer): Future[immutable.Seq[S]] = future()
      def future(takeMax: Int = MaxAllowedSeqSize)(implicit mat: Materializer): Future[immutable.Seq[S]] = {
        source.limit(takeMax.toLong).runWith(Sink.seq)
      }
    }

  }
}
