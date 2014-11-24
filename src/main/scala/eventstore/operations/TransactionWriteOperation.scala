package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import OperationError._
import tcp.PackOut

import scala.util.{ Success, Failure, Try }

case class TransactionWriteOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {

    def operationError(x: OperationError) = x match {
      case PrepareTimeout       => retry()
      case CommitTimeout        => retry()
      case ForwardTimeout       => retry()
      case WrongExpectedVersion => unexpected(x)
      case StreamDeleted        => unexpected(x)
      case InvalidTransaction   => unexpected(x)
      case AccessDenied         => failed(new AccessDeniedException(s"Write access denied"))
    }

    in match {
      case Success(x: TransactionWriteCompleted) => succeed(x)
      case Success(x)                            => unexpected(x)
      case Failure(x: OperationError)            => operationError(x)
      case Failure(OperationTimedOut)            => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))         => retry()
      case Failure(NotHandled(TooBusy))          => retry()
      case Failure(BadRequest)                   => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)             => failed(NotAuthenticatedException(pack))
      case Failure(x)                            => unexpected(x)
    }
  }
}