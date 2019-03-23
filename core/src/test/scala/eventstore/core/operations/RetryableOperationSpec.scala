package eventstore
package core
package operations

import scala.util.control.NoStackTrace
import scala.util.{Failure, Random, Try}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import tcp.PackOut
import util.uuid.randomUuid
import OnIncoming._

class RetryableOperationSpec extends Specification {

  "RetryableOperation" should {

    "proxy id" in new TestScope {
      operation.id mustEqual operation.operation.id
    }

    "proxy client" in new TestScope {
      operation.client mustEqual operation.operation.client
    }

    "proxy version" in new TestScope {
      operation.version mustEqual operation.operation.version
    }

    "wrap underlying connected result if Retry" in new TestScope {

      val expected    = operation.copy(operation = DefaultOp)
      val underlyingM = underlying.copy(connected = OnConnected.Retry(DefaultOp, packOut))
      val operationM  = operation.copy(ongoing = false, operation = underlyingM)

      operationM.connected mustEqual OnConnected.Retry(expected, packOut)
    }

    "return underlying connected result if Stop" in new TestScope {
      operation.copy(ongoing = false).connected mustEqual OnConnected.Stop(in)
    }

    "proxy clientTerminated" in new TestScope {

      val underlyingM = underlying.copy(clientTerminated = Some(packOut))
      val operationM  = operation.copy(operation = underlyingM)

      operationM.clientTerminated must beSome(packOut)
    }

    "wrap underlying on disconnected result if Continue" in new TestScope {

      val expected    = OnDisconnected.Continue(operation.copy(operation = DefaultOp, ongoing = false))
      val underlyingM = underlying.copy(disconnected = OnDisconnected.Continue(DefaultOp))
      val operationM  = operation.copy(operation = underlyingM)

      operationM.disconnected mustEqual expected
    }

    "return underlying on disconnected result if Stop" in new TestScope {
      operation.disconnected mustEqual OnDisconnected.Stop(in)
    }

    "wrap underlying inspectOut result if Some" in new TestScope {

      val underlyingM = underlying.copy(inspectOut = { case `out` => OnOutgoing.Continue(DefaultOp, packOut) })
      val operationM  = operation.copy(operation = underlyingM)

      operationM.inspectOut(out) mustEqual OnOutgoing.Continue(DefaultOp, packOut)
    }

    "return underlying inspectOut result if None" in new TestScope {
      operation.inspectOut.lift(out) must beNone
    }
  }

  "RetryableOperation.inspectIn" should {

    "retry and decrease retries left" in new TestScope {
      operation.inspectIn(forceRetry) must beLike {
        case Retry(RetryableOperation(`underlying`, 0, 1, true), `packOut`) => ok
      }
    }

    "retry and not decrease retries left if disconnected" in new TestScope {
      operation.copy(ongoing = false).inspectIn(forceRetry) must beLike {
        case Retry(RetryableOperation(`underlying`, 1, 1, false), `packOut`) => ok
      }
    }

    "stop if retry limit reached" in new TestScope {
      underlying.inspectIn(forceRetry) must beLike { case Retry(_, _) => ok }
      operation.inspectIn(forceRetry) must beLike {
        case Stop(Failure(_: RetriesLimitReachedException)) => ok
      }

      override def maxRetries = 0
    }

    "reset counter on continue decision" in new TestScope {
      operation.copy(retriesLeft = 0).inspectIn(forceContinue) must beLike {
        case Continue(RetryableOperation(_, 1, 1, _), `forceContinue`) => ok
      }
    }
  }

  private trait TestScope extends Scope {

    import RetryableOperationSpec._

    def maxRetries: Int     = 1

    final val forceRetry    = Failure(new TestException)
    final val forceContinue = Failure(new TestException)
    final val id            = randomUuid
    final val client        = 1
    final val version       = Random.nextInt()
    final val out           = Ping
    final val in            = Try(Pong)
    final val packOut       = PackOut(out, id)
    final val disconnected  = OnDisconnected.Stop(in)
    final val connected     = OnConnected.Stop(in)
    final val terminated    = Option.empty[PackOut]
    final val inspectOut    = InspectOut

    final val inspectIn: OP => Try[In] => OnIncoming[OP] = op => {
      case `forceRetry`    => OnIncoming.Retry[OP](op, packOut)
      case `forceContinue` => OnIncoming.Continue(op, forceContinue)
      case _               => OnIncoming.Ignore
    }

    final val DefaultOp  = DefaultOperation
    final val underlying = TestOperation(client, id, version, inspectIn, inspectOut, connected, disconnected, terminated)
    final val operation  = RetryableOperation(underlying, maxRetries, ongoing = true)
  }

}

object RetryableOperationSpec {

  type Client = Int
  type OP     = Operation[Client]

  class TestException extends Exception with NoStackTrace

  case object InspectOut extends PartialFunction[Out, OnOutgoing[Nothing]] {
    def isDefinedAt(x: Out) = false
    def apply(v1: Out) = sys.error("")
  }

  final case class TestOperation[C](
    client: C,
    id: Uuid,
    version: Int,
    insIn: Operation[C] => Try[In] => OnIncoming[Operation[C]],
    inspectOut: PartialFunction[Out, OnOutgoing[Operation[C]]],
    connected: OnConnected[Operation[C]],
    disconnected: OnDisconnected[Operation[C]],
    clientTerminated: Option[PackOut]
  ) extends Operation[C] {
    def inspectIn(in: Try[In]): OnIncoming[Self] = insIn(this)(in)
  }

  ///

  val DefaultOperation: Operation[Client] = TestOperation[Client](
    client           = 1,
    id               = randomUuid,
    version          = Random.nextInt(),
    insIn            = _ => _ => OnIncoming.Ignore,
    inspectOut       = InspectOut,
    connected        = OnConnected.Stop(Try(Ping)),
    disconnected     = OnDisconnected.Stop(Try(Ping)),
    clientTerminated = None
  )

}