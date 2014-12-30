package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import operations.Decision._
import scala.util.{ Try, Success, Failure }

private[eventstore] case class BaseOperation(
    pack: PackOut,
    client: ActorRef,
    outFunc: Option[OutFunc],
    inspection: Inspection) extends Operation {

  def id = pack.correlationId

  def inspectOut = PartialFunction.empty

  def inspectIn(in: Try[In]): Decision = {
    def retry = Retry(this, pack)

    def unexpected() = {
      val actual = in match {
        case Success(x) => x
        case Failure(x) => x
      }
      val expected = inspection.expected.getSimpleName
      val msg = s"Expected: $expected, actual: $actual"
      Stop(new CommandNotExpectedException(msg))
    }

    def fallback(in: Try[In]): Decision = in match {
      case Success(x)                    => unexpected()
      case Failure(NotHandled(NotReady)) => retry
      case Failure(NotHandled(TooBusy))  => retry
      case Failure(OperationTimedOut)    => Stop(OperationTimeoutException(pack))
      case Failure(BadRequest)           => Stop(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)     => Stop(NotAuthenticatedException(pack))
      case Failure(x)                    => unexpected()
    }

    val pf = inspection.pf andThen {
      case Inspection.Decision.Stop       => Stop(in)
      case Inspection.Decision.Retry      => retry
      case Inspection.Decision.Fail(x)    => Stop(x)
      case Inspection.Decision.Unexpected => unexpected()
    }

    pf.applyOrElse(in, fallback)
  }

  def connectionLost() = {
    Some(copy(outFunc = None))
  }

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(copy(outFunc = Some(outFunc)))
  }

  def clientTerminated = None

  def version = 0
}