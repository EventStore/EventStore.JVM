package eventstore
package operations

import akka.actor.ActorRef
import tcp.PackOut
import scala.util.{ Failure, Try }

case class OutInOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]) extends Operation {
  def id = pack.correlationId

  def inspectIn(in: Try[In]) = {
    in match {
      case Failure(EsException(EsError.OperationTimedOut, _)) =>
        inFunc(Failure(OperationTimeoutException(pack)))

      case _ => inFunc(in)
    }
    //    inFunc(in) // TODO add type checks
    None
  }

  def clientTerminated() = {}

  def inspectOut = PartialFunction.empty

  def connectionLost() = Some(this)

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(this)
  }

  def version = 0
}