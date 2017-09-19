package eventstore.dsl.supports

import spray.json.{ DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat, _ }

/**
 * Json format for all subtypes of an ADT.
 * This format expects each subtype to have its own JsonFormat.
 * It delegates reading and writing to the proper JsonFormat depending on the object subtype determined using :
 *  - The #{getObjectType} function to get the type from an instance when writing to json
 *  - The #{typeFieldName} field in the json when reading from json
 *
 * @param typeFieldName the field to read/write in the json to extract the object subtype
 * @param getObjectType function that returns the object type from a T
 * @param subTypeJsonFormatsMap the map of the jsonFormat for all supported subtypes of T
 * @param outputType whether this format should add the typeFieldName to the json or not (default to true)
 * @tparam T the base type of the ADT
 */
class ADTJsonFormat[T](
  typeFieldName:         String,
  getObjectType:         T => String,
  subTypeJsonFormatsMap: Map[String, JsonFormat[_ <: T]],
  outputType:            Boolean                         = true
) extends RootJsonFormat[T] {
  import DefaultJsonProtocol._

  def jsonFormatForType(elementType: String): JsonFormat[T] = subTypeJsonFormatsMap
    .getOrElse(elementType, deserializationError(s"Trying to fetch the format for an unsupported eventType : $elementType"))
    .asInstanceOf[JsonFormat[T]]

  override def read(json: JsValue): T = json.asJsObject.getFields(typeFieldName).map(_.convertTo[String]) match {
    case Seq(eventType) => jsonFormatForType(eventType).read(json)
    case x              => deserializationError(s"Expected event to contain eventType, but found $x")
  }

  override def write(obj: T): JsValue = {
    val jsonFormat = jsonFormatForType(getObjectType(obj))
    val jsObject = jsonFormat.write(obj).asJsObject
    if (outputType) {
      JsObject(Map(typeFieldName -> JsString(getObjectType(obj))) ++ jsObject.fields)
    } else {
      jsObject
    }
  }
}