package eventstore
package operations

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import NotHandled.NotReady
import tcp.PackOut
import Decision._

import scala.util.Failure

class RetryableOperationSpec extends Specification with Mockito {
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

    "wrap underlying connected result" in new TestScope {
      val result = operation.copy(disconnected = true).connected(outFunc)
      there was one(underlying).connected(outFunc)
      result mustEqual Some(operation.copy(operation = underlying.connected(outFunc).get))
    }

    "proxy clientTerminated" in new TestScope {
      operation.clientTerminated()
      there was one(underlying).clientTerminated()
    }

    "wrap underlying connectionLost result" in new TestScope {
      val result = operation.connectionLost()
      there was one(underlying).connectionLost()
      result mustEqual Some(operation.copy(operation = underlying.connectionLost().get, disconnected = true))
    }

    "wrap underlying inspectOut result" in new TestScope {
      operation.inspectOut
      there was one(underlying).inspectOut
    }
  }

  "RetryableOperation.inspectIn" should {
    "retry and decrease retries left" in new TestScope {
      operation.inspectIn(notReady) must beLike {
        case Retry(RetryableOperation(`underlying`, 0, 1, false), `pack`) => ok
      }
    }

    "retry and not decrease retries left if disconnected" in new TestScope {
      operation.copy(disconnected = true).inspectIn(notReady) must beLike {
        case Retry(RetryableOperation(`underlying`, 1, 1, true), `pack`) => ok
      }
    }

    "stop if retry limit reached" in new TestScope {
      underlying.inspectIn(notReady) must beLike { case Retry(_, _) => ok }
      operation.inspectIn(notReady) must beLike {
        case Stop(Failure(_: RetriesLimitReachedException)) => ok
      }

      override def maxRetries = 0
    }
  }

  private trait TestScope extends Scope {
    val outFunc = mock[OutFunc]
    val out = TransactionCommit(0)
    val pack = PackOut(out)
    val underlying = spy(BaseOperation(pack, null, Some(outFunc), TransactionCommitInspection(out)))
    val operation = RetryableOperation(underlying, maxRetries, disconnected = false)
    val notReady = Failure(NotHandled(NotReady))

    def maxRetries: Int = 1
  }
}
