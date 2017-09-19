package eventstore.dsl

import java.util.UUID

import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.{ Sink, Source }
import eventstore.dsl.supports.SprayJsonFormatsExtension.JavaUUIDFormat
import eventstore.dsl.supports.{ ADTJsonFormat, SprayJsonEventFormats, SprayJsonMetadataFormats }
import eventstore.{ EventData, _ }
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

sealed trait User extends Product
object User {
  val typesMap = Map(
    "Admin" -> Admin.adminJsonFormat,
    "Member" -> Member.memberJsonFormat,
    "God" -> God.godJsonFormat
  )

  implicit val JsonFormat = new ADTJsonFormat[User]("type", _.getClass.getSimpleName, typesMap)
}

case class Member(id: UUID = UUID.randomUUID(), firstName: String, lastName: String = "") extends User
object Member {
  implicit val memberJsonFormat = jsonFormat3(Member.apply)
}

case class Admin(id: UUID = UUID.randomUUID(), firstName: String, lastName: String = "") extends User
object Admin {
  implicit val adminJsonFormat = jsonFormat3(Admin.apply)
}

case class God(id: UUID = UUID.randomUUID(), name: String) extends User
object God {
  implicit val godJsonFormat = jsonFormat2(God.apply)
}

object EsStreamsSpec extends DefaultJsonProtocol {
  implicit val memberEventFormat = SprayJsonEventFormats.format[Member]

  implicit val adminEventFormat = SprayJsonEventFormats.format[Admin]

  implicit val godEventFormat = SprayJsonEventFormats.format[God]

  implicit val userFormat = SprayJsonEventFormats.adtFormat[User](User.typesMap)

  case class CustomMetadata(text: String)

  implicit val customMetadataJsonFormat = jsonFormat1(CustomMetadata)

  implicit val customMetadataMetaFormat = SprayJsonMetadataFormats.format[CustomMetadata]
}

class EsStreamsSpec extends TestConnection {
  implicit val timeout = 10 seconds

  import system.dispatcher

  /*
  override protected def beforeEach() = {
    Await.result(delete(streamId), resetTimeout)
  }
*/

  /*
  "Read Event Store" should {

    "only as with supported event" in {
      val admins = List(Admin(firstName = "joe"), Admin(firstName = "brandon"), Admin(firstName = "steve"), Admin(firstName = "test"))
      val gods = List(God(name = "Root"))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = Admin.getClass.getSimpleName, jsonData = admins.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = God.getClass.getSimpleName, jsonData = gods.map(_.toJson.asJsObject))
        events <- connection.getStream(streamId).read().onlyAs[Admin].future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.futureValue should ===(admins)
    }

    "only as by unsupported event" in {
      val members = List(Member(firstName = "joe"), Member(firstName = "brandon"), Member(firstName = "steve"), Member(firstName = "test"))
      val jsonData = members.map(_.toJson.asJsObject)

      val readedEvents: Future[List[God]] = for {
        _ <- append(streamId = streamId, eventType = Member.getClass.getSimpleName, jsonData = jsonData)
        events <- connection.getStream(streamId).read().onlyAs[God].future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.futureValue shouldBe empty
    }

    "filter with supported event (polymorphism)" in {
      val admins: List[User] = List(Admin(firstName = "joe"), Admin(firstName = "brandon"), Admin(firstName = "steve"), Admin(firstName = "test"))
      val gods: List[User] = List(God(name = "Root"))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = Admin.getClass.getSimpleName, jsonData = admins.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = God.getClass.getSimpleName, jsonData = gods.map(_.toJson.asJsObject))
        events <- connection.getStream(streamId).read().as[User].map { case (offset, either) => offset -> either.right.get }.future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.futureValue should ===(admins ++ gods)
    }

    "filter with not supported event" in {
      val admins: List[User] = List(Admin(firstName = "joe"), Admin(firstName = "brandon"), Admin(firstName = "steve"), Admin(firstName = "test"))
      val jsonBuddy = JsObject(Map("id" -> JsString(UUID.randomUUID().toString), "buddyName" -> JsString("My Buddy")))

      val readedEvents = for {
        _ <- append(streamId = streamId, eventType = Admin.getClass.getSimpleName, jsonData = admins.map(_.toJson.asJsObject))
        _ <- append(streamId = streamId, eventType = "Buddy", jsonData = Seq(jsonBuddy))
        events <- connection.getStream(streamId).read().onlyAs[User].future
      } yield events.map { case (_, m) => m }.toList

      readedEvents.futureValue should ===(admins)
    }

    "read last event on stream not found" in {
      val randomStreamId = EventStream.Id(UUID.randomUUID().toString)

      val readedEvent = connection.getStream(randomStreamId).last().asOpt[Member]

      readedEvent.futureValue should ===(None)
    }

    "read first event on stream not found" in {
      val randomStreamId = EventStream.Id(UUID.randomUUID().toString)

      val readedEvent = connection.getStream(randomStreamId).first().asOpt[Member]

      readedEvent.futureValue should ===(None)
    }

    "read first event on a new stream" in {
      val newStreamID = EventStream.Id(UUID.randomUUID().toString)
      val admin = Admin(firstName = "test")

      val readedElement = for {
        _ <- append(newStreamID, eventType = Admin.getClass.getSimpleName, jsonData = admin.toJson.asJsObject)
        firstElement <- connection.getStream(newStreamID).readOne(Offset.First).asOpt[Admin]
      } yield firstElement

      readedElement.futureValue should ===(Some(admin))

    }

    "read last event on empty stream" in {
      val admin = Admin(firstName = "test")

      val readedElement = for {
        _ <- append(streamId, eventType = Admin.getClass.getSimpleName, jsonData = admin.toJson.asJsObject)
        _ <- delete(streamId)
        lastElement <- connection.getStream(streamId).last().asOpt[Member]
      } yield lastElement

      readedElement.futureValue should ===(None)
    }

    "read last optional event not supported on a stream" in {
      val readedElement = for {
        _ <- append(streamId, eventType = Admin.getClass.getSimpleName, jsonData = Admin(firstName = "test").toJson.asJsObject)
        lastElement <- connection.getStream(streamId).last().asOpt[Member]
      } yield lastElement

      readedElement.futureValue should ===(None)
    }

    "read last event on a stream" in {
      val admin = Admin(firstName = "test")
      val readedElement = for {
        _ <- append(streamId, eventType = Admin.getClass.getSimpleName, jsonData = admin.toJson.asJsObject)
        lastElement <- connection.getStream(streamId).last().asOpt[Admin]
      } yield lastElement

      readedElement.futureValue should ===(Some(admin))
    }

    "first offset of a new stream" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)

      val readedOffset = for {
        _ <- append(newStreamId, eventType = Admin.getClass.getSimpleName, jsonData = Admin(firstName = "test").toJson.asJsObject)
        offset <- connection.getStream(newStreamId).firstOffset().get
      } yield offset

      readedOffset.futureValue.value should be >= 0L
    }

    "first offset of a empty Stream" in {
      val readedOffset = for {
        _ <- delete(streamId)
        offset <- connection.getStream(streamId).firstOffset().getOptAsLong
      } yield offset

      readedOffset.futureValue should ===(None)
    }

    "last offset of a stream" in {
      val readedOffset = for {
        _ <- append(streamId, eventType = Admin.getClass.getSimpleName, jsonData = Admin(firstName = "test").toJson.asJsObject)
        offset <- connection.getStream(streamId).lastOffset().get
      } yield offset

      readedOffset.futureValue.value should be >= 1L
    }

    "last offset of a empty stream" in {
      val readedOffset = for {
        _ <- delete(streamId)
        offset <- connection.getStream(streamId).lastOffset().getOptAsLong
      } yield offset

      readedOffset.futureValue should ===(None)
    }

    "last element of a stream" in {
      val admin = Admin(firstName = "test")
      val readedElement = for {
        _ <- append(streamId, eventType = Admin.getClass.getSimpleName, jsonData = admin.toJson.asJsObject)
        last <- connection.getStream(streamId).last().as[Admin]
      } yield last

      readedElement.futureValue should ===(admin)
    }

    "first element of a new stream" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)
      val admin = Admin(firstName = "test")

      val readedElement = for {
        _ <- append(newStreamId, eventType = Admin.getClass.getSimpleName, jsonData = admin.toJson.asJsObject)
        first <- connection.getStream(newStreamId).first().as[Admin]
      } yield first

      readedElement.futureValue should ===(admin)
    }

    "read to a deleted stream  with new elements" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)

      val stream = connection.getStream(newStreamId)

      val admin1 = Admin(firstName = "test1")
      val admin2 = Admin(firstName = "test2")

      val readedElements = for {
        _ <- stream.append(admin1, admin2)
        _ <- stream.delete()
        _ <- stream.append(admin1, admin2)
        readEvents <- stream.read().onlyAs[Admin].future
      } yield readEvents.map { case (_, m) => m }

      readedElements.futureValue should ===(Seq(admin1, admin2))
    }

    "fetch last element on a deleted stream" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)

      val stream = connection.getStream(newStreamId)

      val admin1 = Admin(firstName = "test1")
      val admin2 = Admin(firstName = "test2")

      val readedElement = for {
        _ <- stream.append(admin1, admin2)
        _ <- stream.delete()
        last <- stream.last().asOpt[Admin]

      } yield last

      readedElement.futureValue should ===(None)
    }

    "fetch last offset on a deleted stream" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)

      val stream = connection.getStream(newStreamId)

      val admin1 = Admin(firstName = "test1")
      val admin2 = Admin(firstName = "test2")

      val readedElement = for {
        _ <- stream.append(admin1, admin2)
        _ <- stream.delete()
        last <- stream.lastOffset().getOpt

      } yield last

      readedElement.futureValue should ===(None)
    }

  }


  "Write Event Store" should {

    "append metadata in a empty stream" in {
      val metadata = CustomMetadata("Custom metadata")
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)

      val readedMetadata = for {
        _ <- connection.getStream(newStreamId).writeMetadata(metadata)
        metadata <- connection.getStream(newStreamId).readMetadata.asOpt[CustomMetadata]
      } yield metadata

      readedMetadata.futureValue should ===(Some(metadata))
    }

    "append event in a new stream" in {
      val newStreamId = EventStream.Id(UUID.randomUUID().toString)
      val secondAdmin = Admin(firstName = "Second")

      val last = for {
        _ <- connection.getStream(newStreamId).append(Admin(firstName = "First"), secondAdmin)
        lastValue <- connection.getStream(newStreamId).last().as[Admin]
      } yield lastValue

      last.futureValue should ===(secondAdmin)
    }

    //    "append one event on not empty stream" in {}
    //    "append one event on an empty stream" in {}
    //    "append one event of a stream not found" in {}

  }
*/

  abstract class TestScope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)
    implicit val materializer = ActorMaterializer()

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
