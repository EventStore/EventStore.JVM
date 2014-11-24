package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import ScavengeError._
import tcp.PackOut
import scala.util.{ Failure, Success, Try }

case class ScavengeDatabaseOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc])
    extends AbstractOperation {
  def inspectIn(in: Try[In]) = {
    in match {
      case Success(x: ScavengeDatabaseCompleted) => succeed(x)
      case Success(x)                            => unexpected(x)
      case Failure(InProgress)                   => failed(ScavengeInProgressException)
      case Failure(Failed(error))                => failed(new ScavengeFailedException(error.orNull))
      case Failure(NotHandled(NotReady))         => retry()
      case Failure(NotHandled(TooBusy))          => retry()
      case Failure(OperationTimedOut)            => failed(OperationTimeoutException(pack))
      case Failure(x)                            => unexpected(x)
    }
  }
}
