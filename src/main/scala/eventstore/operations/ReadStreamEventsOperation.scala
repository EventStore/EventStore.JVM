package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import ReadStreamEventsError._
import scala.util.{ Success, Failure, Try }

case class ReadStreamEventsOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {
    val readStreamEvents = pack.message.asInstanceOf[ReadStreamEvents]
    val streamId = readStreamEvents.streamId

    def readStreamEventsError(x: ReadStreamEventsError) = x match {
      case StreamNotFound => StreamNotFoundException(streamId)
      case StreamDeleted  => new StreamDeletedException(s"Read failed due to $streamId has been deleted")
      case Error(error)   => new ServerErrorException(error.orNull)
      case AccessDenied   => new AccessDeniedException(s"Read access denied for $streamId")
    }

    in match {
      case Success(x: ReadStreamEventsCompleted) => succeed(x)
      case Success(x)                            => unexpected(x)
      case Failure(x: ReadStreamEventsError)     => failed(readStreamEventsError(x))
      case Failure(OperationTimedOut)            => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))         => retry()
      case Failure(NotHandled(TooBusy))          => retry()
      case Failure(x)                            => unexpected(x)
    }
  }
}