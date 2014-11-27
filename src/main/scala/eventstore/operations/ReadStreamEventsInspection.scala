package eventstore
package operations

import ReadStreamEventsError._
import Inspection.Decision.Fail

private[eventstore] class ReadStreamEventsInspection(out: ReadStreamEvents)
    extends ErrorInspection[ReadStreamEventsCompleted, ReadStreamEventsError] {

  def decision(error: ReadStreamEventsError) = {
    val result = error match {
      case StreamNotFound => StreamNotFoundException(streamId)
      case StreamDeleted  => new StreamDeletedException(s"Read failed due to $streamId has been deleted")
      case Error(error)   => new ServerErrorException(error.orNull)
      case AccessDenied   => new AccessDeniedException(s"Read access denied for $streamId")
    }

    Fail(result)
  }

  def streamId = out.streamId
}