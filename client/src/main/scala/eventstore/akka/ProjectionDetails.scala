package eventstore
package akka

import io.circe._
import ProjectionsClient.{ProjectionMode, ProjectionStatus}

import scala.util.Try

object ProjectionDetails {

  implicit val decoderForProjectionStatus: Decoder[ProjectionStatus] =
    Decoder[String].map(s => ProjectionStatus(s))

  implicit val decoderForProjectionMode: Decoder[ProjectionMode] =
    Decoder[String].emapTry(s => Try(ProjectionMode(s)))

  implicit val decoderForProjectionDetails: Decoder[ProjectionDetails] =
    Decoder.forProduct11(
      "name",
      "effectiveName",
      "version",
      "epoch",
      "status",
      "stateReason",
      "mode",
      "writesInProgress",
      "readsInProgress",
      "progress",
      "bufferedEvents"
    )(ProjectionDetails.apply)

}

final case class ProjectionDetails(
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