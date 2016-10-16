package eventstore

import eventstore.ProjectionsClient.{ ProjectionMode, ProjectionStatus }
import play.api.libs.json._

object ProjectionDetails {

  implicit val projectionStatusReads = implicitly[Reads[String]].map(ProjectionStatus(_))

  implicit val projectionModeReads = implicitly[Reads[String]].map(ProjectionMode(_))

  implicit val projectionDetailsJsonFormat = Json.reads[ProjectionDetails]
}

case class ProjectionDetails(name: String,
                             effectiveName: String,
                             version: Int,
                             epoch: Int,
                             status: ProjectionStatus,
                             stateReason: String,
                             mode: ProjectionMode,
                             writesInProgress: Int,
                             readsInProgress: Int,
                             progress: Double,
                             bufferedEvents: Int)