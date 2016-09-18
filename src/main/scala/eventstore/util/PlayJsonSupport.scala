package eventstore.util

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.util.ByteString
import play.api.libs.json._

object PlayJsonSupport {

  private val jsonStringUnmarshaller = Unmarshaller
    .byteStringUnmarshaller
    .forContentTypes(`application/json`)
    .mapWithCharset {
      case (ByteString.empty, _) => throw Unmarshaller.NoContentException
      case (data, charset)       => data.decodeString(charset.nioCharset.name)
    }

  private val jsonStringMarshaller =
    Marshaller.stringMarshaller(`application/json`)

  implicit def playJsonUnmarshaller[A](implicit reads: Reads[A]): FromEntityUnmarshaller[A] = {
    def read(json: JsValue) = reads
      .reads(json)
      .recoverTotal(error => throw new IllegalArgumentException(JsError.toJson(error).toString))
    jsonStringUnmarshaller.map(data => read(Json.parse(data)))
  }

  implicit def playJsonMarshaller[A](implicit writes: Writes[A], printer: JsValue => String = Json.prettyPrint): ToEntityMarshaller[A] =
    jsonStringMarshaller.compose(printer).compose(writes.writes)
}