package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import scala.util.{ Try, Success, Failure }

private[eventstore] case class BaseOperation(
    pack: PackOut,
    client: ActorRef,
    inFunc: InFunc,
    outFunc: Option[OutFunc],
    inspection: Inspection) extends Operation {
  import Inspection.Decision._

  def id = pack.correlationId

  def inspectOut = PartialFunction.empty

  def inspectIn(in: Try[In]) = {
    def stop(x: Try[In]) = {
      inFunc(x)
      None
    }

    def retry() = {
      outFunc.foreach { outFunc => outFunc(pack) }
      Some(this)
    }

    def unexpected() = {
      val actual = in match {
        case Success(x) => x
        case Failure(x) => x
      }
      val expected = inspection.expected
      val exception = new CommandNotExpectedException(s"Expected: $expected, actual: $actual")
      stop(Failure(exception))
    }

    inspection.pf.applyOrElse(in, fallback) match {
      case Stop       => stop(in)
      case Retry      => retry()
      case Fail(x)    => stop(Failure(x))
      case Unexpected => unexpected()
    }
  }

  def connectionLost() = {
    Some(copy(outFunc = None))
  }

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(copy(outFunc = Some(outFunc)))
  }

  def clientTerminated() = {}

  def version = 0

  private def fallback(in: Try[In]) = {
    in match {
      case Success(x)                    => Unexpected
      case Failure(NotHandled(NotReady)) => Retry
      case Failure(NotHandled(TooBusy))  => Retry
      case Failure(OperationTimedOut)    => Fail(OperationTimeoutException(pack))
      case Failure(BadRequest)           => Fail(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)     => Fail(NotAuthenticatedException(pack))
      case Failure(x)                    => Unexpected
    }
  }
}