package eventstore
package operations

import ReadAllEventsError._
import Inspection.Decision._

private[eventstore] class ReadAllEventsInspection(out: ReadAllEvents)
    extends ErrorInspection[ReadAllEventsCompleted, ReadAllEventsError] {

  def decision(error: ReadAllEventsError) = {
    error match {
      case Error(error) => Fail(new ServerErrorException(error.orNull))
      case AccessDenied => Fail(new AccessDeniedException(s"Read access denied for $streamId"))
    }
  }

  def streamId = EventStream.All
}