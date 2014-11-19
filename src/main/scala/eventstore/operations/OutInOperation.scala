package eventstore
package operations

import akka.actor.ActorRef
import eventstore.tcp.PackOut
import scala.util.{ Failure, Try }

case class OutInOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]) extends Operation {
  def id = pack.correlationId

  def inspectIn(in: Try[In]) = {
    inFunc(in) // TODO add type checks
    None
  }

  def clientTerminated() = {}

  def inspectOut = PartialFunction.empty

  def connectionLost() = Some(this)

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(this)
  }

  def timedOut = {
    val timedOut = EsError.OperationTimedOut(pack.message)
    inspectIn(Failure(EsException(timedOut)))
  }

  def version = 0
}