package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import OperationError._
import tcp.PackOut
import scala.util.{ Success, Failure, Try }

case class TransactionCommitOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {
    val transactionCommit = pack.message.asInstanceOf[TransactionCommit]
    val transactionId = transactionCommit.transactionId

    def operationError(x: OperationError) = x match {
      case PrepareTimeout       => retry()
      case CommitTimeout        => retry()
      case ForwardTimeout       => retry()
      case WrongExpectedVersion => failed(WrongExpectedVersionException(s"Transaction commit failed due to WrongExpectedVersion, transactionId: $transactionId"))
      case StreamDeleted        => failed(new StreamDeletedException(s"Transaction commit due to stream has been deleted, transactionId: $transactionId"))
      case InvalidTransaction   => failed(InvalidTransactionException)
      case AccessDenied         => failed(new AccessDeniedException(s"Write access denied"))
    }

    in match {
      case Success(x: TransactionCommitCompleted) => succeed(x)
      case Success(x)                             => unexpected(x)
      case Failure(x: OperationError)             => operationError(x)
      case Failure(OperationTimedOut)             => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady))          => retry()
      case Failure(NotHandled(TooBusy))           => retry()
      case Failure(BadRequest)                    => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)              => failed(NotAuthenticatedException(pack))
      case Failure(x)                             => unexpected(x)
    }
  }
}