package eventstore.dsl.supports

import eventstore.Content
import eventstore.dsl.{ MetadataFormat, MetadataReader, MetadataWriter }
import spray.json._

object SprayJsonMetadataFormats {
  trait SprayJsonMetadataReaderLike[M] extends MetadataReader[M] {
    implicit val jsonReader: JsonReader[M]

    def read(content: Content): Option[M] = content match {
      case Content.Json(jsValue) =>
        val payload = jsValue.parseJson.asInstanceOf[JsObject]
        Some(doRead(payload))
      case contentNotSupported => deserializationError(s"Only json format is supported : $contentNotSupported")
    }

    def doRead(event: JsObject): M = event.convertTo[M]
  }

  class SprayJsonMetadataReader[M](implicit val jsonReader: JsonReader[M]) extends SprayJsonMetadataReaderLike[M]

  trait SprayJsonMetadataWriterLike[M] extends MetadataWriter[M] {
    implicit val jsonWriter: JsonWriter[M]

    def write(data: M): Content = {
      val json = doWrite(data)
      Content.Json(json.compactPrint)
    }

    def doWrite(data: M): JsObject = data.toJson.asJsObject
  }

  class SprayJsonMetadataWriter[M](implicit val jsonWriter: JsonWriter[M]) extends SprayJsonMetadataWriterLike[M]

  class SprayJsonMetadataFormat[M](implicit val jsonReader: JsonReader[M], val jsonWriter: JsonWriter[M])
    extends MetadataFormat[M] with SprayJsonMetadataReaderLike[M] with SprayJsonMetadataWriterLike[M]

  def reader[M](implicit reader: JsonReader[M]): SprayJsonMetadataReader[M] = new SprayJsonMetadataReader[M]

  def writer[M](implicit writer: JsonWriter[M]): SprayJsonMetadataWriter[M] = new SprayJsonMetadataWriter[M]

  def format[M](implicit jsonFormat: JsonFormat[M]): SprayJsonMetadataFormat[M] = new SprayJsonMetadataFormat[M]
}
