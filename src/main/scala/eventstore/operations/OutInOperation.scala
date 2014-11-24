package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import scala.util.{ Success, Failure, Try }

case class OutInOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {

  def inspectIn(in: Try[In]) = {
    in match {
      case Success(x)                    => succeed(x)
      case Failure(OperationTimedOut)    => failed(OperationTimeoutException(pack))
      case Failure(NotHandled(NotReady)) => retry()
      case Failure(NotHandled(TooBusy))  => retry()
      case Failure(BadRequest)           => failed(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)     => failed(NotAuthenticatedException(pack))
      case Failure(x)                    => failed(x)
    }
  }
}