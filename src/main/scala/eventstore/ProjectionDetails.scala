package eventstore

import eventstore.ProjectionsClient.{ ProjectionMode, ProjectionStatus }
import spray.json._

object ProjectionDetails {
  import DefaultJsonProtocol._

  implicit val projectionStatusFormat = lift(new JsonReader[ProjectionStatus] {
    def read(json: JsValue): ProjectionStatus = json match {
      case JsString(x) => ProjectionStatus(x)
      case _           => deserializationError(s"expected projection status as string, got $json")
    }
  })

  implicit val projectionModeFormat = lift(new JsonReader[ProjectionMode] {
    def read(json: JsValue): ProjectionMode = json match {
      case JsString(x) => ProjectionMode(x)
      case _           => deserializationError(s"expected projection mode as string, got $json")
    }
  })

  implicit val projectionDetailsJsonFormat = jsonFormat11(ProjectionDetails.apply)
}

case class ProjectionDetails(
  name:             String,
  effectiveName:    String,
  version:          Int,
  epoch:            Int,
  status:           ProjectionStatus,
  stateReason:      String,
  mode:             ProjectionMode,
  writesInProgress: Int,
  readsInProgress:  Int,
  progress:         Double,
  bufferedEvents:   Int
)