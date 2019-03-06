package eventstore
package operations

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import eventstore.tcp.PackOut
import eventstore.operations.OnIncoming._
import scala.util.control.NoStackTrace
import scala.util.{ Try, Random, Failure }

class RetryableOperationSpec extends Specification with Mockito {
  "RetryableOperation" should {
    "proxy id" in new TestScope {
      operation.id mustEqual operation.operation.id
      there was two(underlying).id
    }

    "proxy client" in new TestScope {
      operation.client mustEqual operation.operation.client
      there was two(underlying).client
    }

    "proxy version" in new TestScope {
      operation.version mustEqual operation.operation.version
      there was two(underlying).version
    }

    "wrap underlying connected result if Retry" in new TestScope {
      val result = mock[OP]
      underlying.connected returns OnConnected.Retry(result, pack)
      val expected = operation.copy(operation = result)
      operation.copy(ongoing = false).connected mustEqual OnConnected.Retry(expected, pack)
    }

    "return underlying connected result if Stop" in new TestScope {
      operation.copy(ongoing = false).connected mustEqual OnConnected.Stop(in)
      there was one(underlying).connected
    }

    "proxy clientTerminated" in new TestScope {
      operation.clientTerminated mustEqual underlying.clientTerminated
      there was two(underlying).clientTerminated
    }

    "wrap underlying on disconnected result if Continue" in new TestScope {
      val result = mock[OP]
      underlying.disconnected returns OnDisconnected.Continue(result)
      operation.disconnected mustEqual OnDisconnected.Continue(operation.copy(operation = result, ongoing = false))
      there was one(underlying).disconnected
    }

    "return underlying on disconnected result if Stop" in new TestScope {
      operation.disconnected mustEqual OnDisconnected.Stop(in)
      there was one(underlying).disconnected
    }

    "wrap underlying inspectOut result if Some" in new TestScope {
      val result = mock[OP]
      val pf: PartialFunction[Out, OnOutgoing[OP]] = {
        case `out` => OnOutgoing.Continue(result, pack)
      }
      underlying.inspectOut returns pf
      operation.inspectOut(out) mustEqual OnOutgoing.Continue(result, pack)
      there was one(underlying).inspectOut
    }

    "return underlying inspectOut result if None" in new TestScope {
      operation.inspectOut.lift(out) must beNone
      there was one(underlying).inspectOut
      there was one(inspectOut).isDefinedAt(out)
    }
  }

  "RetryableOperation.inspectIn" should {
    "retry and decrease retries left" in new TestScope {
      operation.inspectIn(forceRetry) must beLike {
        case Retry(RetryableOperation(`underlying`, 0, 1, true), `pack`) => ok
      }
    }

    "retry and not decrease retries left if disconnected" in new TestScope {
      operation.copy(ongoing = false).inspectIn(forceRetry) must beLike {
        case Retry(RetryableOperation(`underlying`, 1, 1, false), `pack`) => ok
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

    type OP = Operation[Unit]

    val forceRetry = Failure(new TestException)
    val forceContinue = Failure(new TestException)
    val out = mock[Out]
    val in = Try(Pong)
    val pack = PackOut(out)
    val inspectOut = spy(new InspectOut)
    val underlying = {
      val operation = mock[OP]
      operation.clientTerminated returns None
      operation.id returns randomUuid
      operation.version returns Random.nextInt()
      operation.connected returns OnConnected.Stop(in)
      operation.disconnected returns OnDisconnected.Stop(in)
      operation.inspectOut returns inspectOut
      operation.inspectIn(forceRetry) returns OnIncoming.Retry(operation, pack)
      operation.inspectIn(forceContinue) returns OnIncoming.Continue(operation, forceContinue)
      operation
    }
    val operation = RetryableOperation(underlying, maxRetries, ongoing = true)

    def maxRetries: Int = 1

    class TestException extends Exception with NoStackTrace
  }

  class InspectOut extends PartialFunction[Out, OnOutgoing[Nothing]] {
    def isDefinedAt(x: Out) = false
    def apply(v1: Out) = sys.error("")
  }
}