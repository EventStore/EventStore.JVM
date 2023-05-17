package eventstore
package core
package operations

import ReadAllEventsError._
import Inspection.Decision._

private[eventstore] final case class ReadAllEventsInspection(out: ReadAllEvents)
    extends ErrorInspection[ReadAllEventsCompleted, ReadAllEventsError] {

  def decision(error: ReadAllEventsError) = {
    error match {
      case e: Error     => Fail(ServerErrorException(e.message.getOrElse(e.toString)))
      case AccessDenied => Fail(AccessDeniedException(s"Read access denied for $streamId"))
      case Unrecognized => Fail(UnrecognizedException)
    }
  }

  def streamId = EventStream.All
}