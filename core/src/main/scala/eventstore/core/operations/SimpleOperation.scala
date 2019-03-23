package eventstore
package core
package operations

import scala.util.{Failure, Success, Try}
import eventstore.core.tcp.PackOut
import NotHandled.{ NotReady, TooBusy }

private[eventstore] final case class SimpleOperation[C](
  pack:       PackOut,
  client:     C,
  inspection: Inspection
) extends Operation[C] {

  def id               = pack.correlationId
  def version          = 0
  def disconnected     = OnDisconnected.Continue(this)
  def connected        = OnConnected.Retry(this, pack)
  def clientTerminated = None

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
      case Success(_)                    => unexpected()
      case Failure(NotHandled(NotReady)) => retry
      case Failure(NotHandled(TooBusy))  => retry
      case Failure(OperationTimedOut)    => Stop(OperationTimeoutException(pack))
      case Failure(BadRequest)           => Stop(new ServerErrorException(s"Bad request: $pack"))
      case Failure(NotAuthenticated)     => Stop(NotAuthenticatedException(pack))
      case Failure(_)                    => unexpected()
    }

    val pf = inspection.pf andThen {
      case Inspection.Decision.Stop       => Stop(in)
      case Inspection.Decision.Retry      => retry
      case Inspection.Decision.Fail(x)    => Stop(x)
      case Inspection.Decision.Unexpected => unexpected()
    }

    pf.applyOrElse(in, fallback)
  }

  def inspectOut = PartialFunction.empty

}