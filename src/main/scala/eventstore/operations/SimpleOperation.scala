package eventstore
package operations

import NotHandled.{ NotReady, TooBusy }
import eventstore.tcp.PackOut

import scala.util.{ Failure, Success, Try }

private[eventstore] case class SimpleOperation[C](
    pack:       PackOut,
    client:     C,
    inspection: Inspection
) extends Operation[C] {

  def id = pack.correlationId

  def inspectOut = PartialFunction.empty

  def inspectIn(in: Try[In]): OnIncoming[Operation[C]] = {
    import OnIncoming._

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

    def fallback(in: Try[In]): OnIncoming[Operation[C]] = in match {
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

  def disconnected = OnDisconnected.Continue(this)

  def connected = OnConnected.Retry(this, pack)

  def clientTerminated = None

  def version = 0
}