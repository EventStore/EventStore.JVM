package eventstore.dsl.supports

import java.util.UUID

import eventstore.dsl.{ EsEvent, EventFormat, EventReader, EventWriter }
import eventstore.{ Content, Event, EventData }
import spray.json._

import scala.reflect.ClassTag
import scala.util.control.Exception._

trait SprayJsonFormatsExtension {
  implicit object JavaUUIDFormat extends JsonFormat[UUID] {
    override def write(obj: UUID): JsValue = JsString(obj.toString)

    override def read(json: JsValue): UUID = json match {
      case JsString(value) =>
        catching(classOf[IllegalArgumentException])
          .opt(UUID.fromString(value))
          .getOrElse(deserializationError(s"Expected UUID, but got: $value"))

      case x => deserializationError(s"Java UUID : Expected UUID, but got $x")
    }
  }
}

object SprayJsonFormatsExtension extends SprayJsonFormatsExtension

object SprayJsonEventFormats {
  trait JsonEventReader[T] extends EventReader[T] {
    implicit val eventClassTag: ClassTag[T]

    final def read(event: Event): EsEvent[T] = {
      event.data.data match {
        case Content.Json(jsonValue) => EsEvent(event, doRead(event, jsonValue))
        case notSupported            => deserializationError(s"Only json format is supported : ${notSupported.contentType}")
      }
    }

    def doRead(source: Event, json: String): T

    def supports(event: Event): Boolean = event.data.eventType == implicitly[ClassTag[T]].runtimeClass.getSimpleName
  }

  class SprayJsonEventReader[T: JsonReader](implicit val eventClassTag: ClassTag[T]) extends JsonEventReader[T] {
    def doRead(source: Event, json: String): T = json.parseJson.convertTo[T]
  }

  trait JsonEventWriter[T] extends EventWriter[T] {
    private val specialCharacters = List("$", "%", "_", " ", "#")

    final def write(data: T): EventData = {
      EventData(eventType(data), eventId(data), data = Content.Json(doWrite(data)))
    }

    def eventType(data: T): String = {
      val eventType = data.getClass.getSimpleName
      require(!specialCharacters.exists(eventType.contains), s"An event type should never contains special characters ($specialCharacters) $eventType")
      eventType
    }

    def doWrite(data: T): String
  }

  class SprayJsonEventWriter[T: JsonWriter] extends JsonEventWriter[T] {
    def doWrite(data: T): String = data.toJson.asJsObject.compactPrint
  }

  trait JsonEventFormat[T] extends EventFormat[T] with JsonEventReader[T] with JsonEventWriter[T]

  class SprayJsonEventFormat[T: JsonFormat](implicit val eventClassTag: ClassTag[T]) extends JsonEventFormat[T] {
    def doWrite(data: T): String = data.toJson.asJsObject.compactPrint
    def doRead(source: Event, json: String): T = json.parseJson.convertTo[T]
  }

  class AdtSprayJsonEventFormat[T](subTypeJsonFormatsMap: Map[String, JsonFormat[_ <: T]])(implicit val eventClassTag: ClassTag[T]) extends JsonEventFormat[T] {
    override def doWrite(data: T): String = {
      subTypeJsonFormatsMap
        .getOrElse(eventType(data), throw new IllegalArgumentException(s"Trying to write unsupported event type ${eventType(data)}"))
        .asInstanceOf[JsonFormat[T]]
        .write(data)
        .asJsObject
        .compactPrint
    }

    override def doRead(source: Event, json: String): T = {
      val eventType = source.data.eventType
      subTypeJsonFormatsMap
        .getOrElse(eventType, throw new IllegalArgumentException(s"Trying to write unsupported event type ${eventType}"))
        .asInstanceOf[JsonFormat[T]]
        .read(json.parseJson)
    }

    override def supports(event: Event): Boolean = subTypeJsonFormatsMap.keySet.contains(event.data.eventType)
  }

  def reader[T: JsonReader: ClassTag]: JsonEventReader[T] = new SprayJsonEventReader[T]

  def writer[T: JsonWriter]: JsonEventWriter[T] = new SprayJsonEventWriter[T]

  def format[T: JsonFormat: ClassTag]: JsonEventFormat[T] = new SprayJsonEventFormat[T]

  def adtFormat[T: ClassTag](subTypeJsonFormatsMap: Map[String, JsonFormat[_ <: T]]): JsonEventFormat[T] =
    new AdtSprayJsonEventFormat[T](subTypeJsonFormatsMap)

}

