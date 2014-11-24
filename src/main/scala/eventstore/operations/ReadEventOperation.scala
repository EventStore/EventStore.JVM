package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import eventstore.tcp.PackOut
import ReadEventError._
import scala.util.{ Success, Failure, Try }

case class ReadEventOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]) extends AbstractOperation {
  def inspectIn(in: Try[In]) = {
    val readEvent = pack.message.asInstanceOf[ReadEvent]
    val streamId = readEvent.streamId

    def readEventError(x: ReadEventError) = x match {
      case EventNotFound  => EventNotFoundException(streamId, readEvent.eventNumber)
      case StreamNotFound => StreamNotFoundException(streamId)
      case StreamDeleted  => new StreamDeletedException(s"Read failed due to $streamId has been deleted")
      case Error(error)   => new ServerErrorException(error.orNull)
      case AccessDenied   => new AccessDeniedException(s"Read access denied for $streamId")
    }

    in match {
      case Success(x: ReadEventCompleted) => succeed(x)
      case Success(x)                     => unexpected(x)
      case Failure(x: ReadEventError)     => failed(readEventError(x))
      case Failure(OperationTimedOut)     => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))  => retry()
      case Failure(NotHandled(TooBusy))   => retry()
      case Failure(BadRequest)            => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)      => failed(NotAuthenticatedException(pack))
      case Failure(x)                     => unexpected(x)
    }
  }
}