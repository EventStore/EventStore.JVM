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
    inspection: Inspection,
    retriesLeft: Int,
    maxRetries: Int) extends Operation {
  import Inspection.Decision._

  def id = pack.correlationId

  def inspectOut = PartialFunction.empty

  def inspectIn(in: Try[In]) = {
    def stop(x: Try[In]) = {
      inFunc(x)
      None
    }

    def retry() = {
      outFunc match {
        case None => Some(this)
        case Some(outFunc) =>
          if (retriesLeft > 0) {
            outFunc(pack)
            Some(copy(retriesLeft = retriesLeft - 1))
          } else {
            inFunc(Failure(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries")))
            None
          }
      }
    }

    def unexpected() = {
      val actual = in match {
        case Success(x) => x
        case Failure(x) => x
      }
      val expected = inspection.expected.getSimpleName
      val msg = s"Expected: $expected, actual: $actual"
      stop(Failure(new CommandNotExpectedException(msg)))
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
    // TODO correlate maxRetries with outFunc(pack)
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

private[eventstore] object BaseOperation {
  def apply(
    pack: PackOut,
    client: ActorRef,
    inFunc: InFunc,
    outFunc: Option[OutFunc],
    inspection: Inspection,
    retries: Int): BaseOperation = BaseOperation(
    pack,
    client,
    inFunc,
    outFunc,
    inspection,
    retriesLeft = retries,
    maxRetries = retries)
}