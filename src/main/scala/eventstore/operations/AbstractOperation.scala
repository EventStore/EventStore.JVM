package eventstore
package operations

import tcp.PackOut
import scala.util.{ Success, Failure }

private[eventstore] trait AbstractOperation extends Operation {

  def pack: PackOut

  def outFunc: Option[OutFunc]

  def inFunc: InFunc

  def id = pack.correlationId

  def clientTerminated() = {}

  def inspectOut = PartialFunction.empty

  def connectionLost() = Some(this) // TODO copy this without connection

  def connected(outFunc: OutFunc) = {
    outFunc(pack)

    // TODO copy this with connection

    Some(this)
  }

  def version = 0

  def unexpected(x: Any) = failed(new CommandNotExpectedException(x.toString))

  def retry() = {
    outFunc.foreach { outFunc => outFunc(pack) }
    Some(this)
  }

  def failed(x: Throwable) = {
    inFunc(Failure(x))
    None
  }

  def succeed(x: In) = {
    inFunc(Success(x))
    None
  }
}
