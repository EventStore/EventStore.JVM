package eventstore
package core
package operations

import ReadStreamEventsError._
import Inspection.Decision.Fail

private[eventstore] final case class ReadStreamEventsInspection(out: ReadStreamEvents)
    extends ErrorInspection[ReadStreamEventsCompleted, ReadStreamEventsError] {

  def decision(error: ReadStreamEventsError) = {
    val result = error match {
      case StreamNotFound => StreamNotFoundException(streamId)
      case StreamDeleted  => StreamDeletedException(s"Read failed due to $streamId has been deleted")
      case e: Error       => ServerErrorException(e.message.getOrElse(e.toString))
      case AccessDenied   => AccessDeniedException(s"Read access denied for $streamId")
    }

    Fail(result)
  }

  def streamId = out.streamId
}