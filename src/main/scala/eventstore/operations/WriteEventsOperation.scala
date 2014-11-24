package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import OperationError._
import scala.util.{ Success, Failure, Try }

case class WriteEventsOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {
    val writeEvents = pack.message.asInstanceOf[WriteEvents]
    val streamId = writeEvents.streamId
    val expectedVersion = writeEvents.expectedVersion

    def operationError(x: OperationError) = x match {
      case PrepareTimeout       => retry()
      case CommitTimeout        => retry()
      case ForwardTimeout       => retry()
      case WrongExpectedVersion => failed(WrongExpectedVersionException(s"Write failed due to WrongExpectedVersion: $streamId, $expectedVersion"))
      case StreamDeleted        => failed(new StreamDeletedException(s"Write failed due to $streamId has been deleted"))
      case InvalidTransaction   => failed(InvalidTransactionException)
      case AccessDenied         => failed(new AccessDeniedException(s"Write access denied for $streamId"))
    }

    in match {
      case Success(x: WriteEventsCompleted) => succeed(x)
      case Success(x)                       => unexpected(x)
      case Failure(x: OperationError)       => operationError(x)
      case Failure(OperationTimedOut)       => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))    => retry()
      case Failure(NotHandled(TooBusy))     => retry()
      case Failure(BadRequest)              => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)        => failed(NotAuthenticatedException(pack))
      case Failure(x)                       => unexpected(x)
    }
  }
}