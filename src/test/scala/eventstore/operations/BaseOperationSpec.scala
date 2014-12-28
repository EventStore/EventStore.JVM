package eventstore
package operations

import NotHandled.{ TooBusy, NotReady }
import Decision._
import tcp.PackOut
import scala.util.control.NoStackTrace
import scala.util.{ Success, Failure }

class BaseOperationSpec extends OperationSpec {
  "BaseOperation" should {
    "return id equal to correlationId" in new BaseOperationScope {
      operation.id mustEqual pack.correlationId
    }

    "drop OutFunc on connectionLost" in new BaseOperationScope {
      val actual = operation.connectionLost()
      actual must beSome
      actual.get.outFunc must beNone
      there were noCallsTo(outFunc, inFunc)
    }

    "save new OutFunc on connected and retry" in new BaseOperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc mustEqual Some(newOutFunc)
      there were noCallsTo(outFunc, inFunc)
      there was one(newOutFunc).apply(pack)
    }

    "replace OutFunc on connected and retry" in new BaseOperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc mustEqual Some(newOutFunc)
      there were noCallsTo(outFunc, inFunc)
      there was one(newOutFunc).apply(pack)
    }

    "ignore clientTerminated" in new BaseOperationScope {
      operation.clientTerminated()
      there were noCallsTo(outFunc, inFunc)
    }

    "ignore out messages" in new BaseOperationScope {
      operation.inspectOut mustEqual PartialFunction.empty
      operation.inspectOut.isDefinedAt(Ping) must beFalse
      there were noCallsTo(outFunc, inFunc)
    }

    "stop on success" in new BaseOperationScope {
      operation.inspectIn(Success(Pong)) mustEqual Stop(Pong)
    }

    "stop on expected error" in new BaseOperationScope {
      operation.inspectIn(Failure(TestError)) mustEqual Stop(TestException)
    }

    "retry on NotReady" in new BaseOperationScope {
      val result = operation.inspectIn(Failure(NotHandled(NotReady)))
      result mustEqual Retry(operation.copy(retriesLeft = operation.retriesLeft - 1), pack)
    }

    "retry on TooBusy" in new BaseOperationScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation.copy(retriesLeft = operation.retriesLeft - 1), pack)
    }

    "keep retrying until max retries limit reached" in new BaseOperationScope {
      val failure = Failure(NotHandled(TooBusy))

      val o2 = operation.copy(retriesLeft = operation.retriesLeft - 1)
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(o2, pack)
      o2.inspectIn(failure) must beLike {
        case Stop(Failure(_: RetriesLimitReachedException)) => ok
      }
    }

    "stop on OperationTimedOut" in new BaseOperationScope {
      operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new BaseOperationScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beLike {
        case Stop(Failure(_: NotAuthenticatedException)) => ok
      }
    }

    "stop on BadRequest" in new BaseOperationScope {
      operation.inspectIn(Failure(BadRequest)) must beLike {
        case Stop(Failure(_: ServerErrorException)) => ok
      }
    }

    "stop on unexpected" in new BaseOperationScope {
      operation.inspectIn(Success(Authenticated)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected error" in new BaseOperationScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "always return 0 for version" in new BaseOperationScope {
      operation.version mustEqual 0
      val o1 = operation.connectionLost().get
      o1.version mustEqual 0
      val o2 = operation.connected(outFunc).get
      o2.version mustEqual 0
    }
  }

  trait BaseOperationScope extends OperationScope {
    val pack = PackOut(Ping)
    val inspection = new Inspection {
      def expected = Pong.getClass
      def pf = {
        case x @ Success(Pong)  => Inspection.Decision.Stop
        case Failure(TestError) => Inspection.Decision.Fail(TestException)
      }
    }

    val operation = BaseOperation(pack, client, Some(outFunc), inspection, 1)

    object TestError extends RuntimeException with NoStackTrace
    object TestException extends EsException with NoStackTrace
  }
}