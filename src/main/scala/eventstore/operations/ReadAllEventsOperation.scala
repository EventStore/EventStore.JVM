package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import eventstore.ReadAllEventsError._
import scala.util.{ Success, Failure, Try }

case class ReadAllEventsOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {
    val streamId = EventStream.All

    def readAllEventsError(x: ReadAllEventsError) = x match {
      case Error(error) => new ServerErrorException(error.orNull)
      case AccessDenied => new AccessDeniedException(s"Read access denied for $streamId")
    }

    in match {
      case Success(x: ReadAllEventsCompleted) => succeed(x)
      case Success(x)                         => unexpected(x)
      case Failure(x: ReadAllEventsError)     => failed(readAllEventsError(x))
      case Failure(OperationTimedOut)         => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))      => retry()
      case Failure(NotHandled(TooBusy))       => retry()
      case Failure(BadRequest)                => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)          => failed(NotAuthenticatedException(pack))
      case Failure(x)                         => unexpected(x)
    }
  }
}