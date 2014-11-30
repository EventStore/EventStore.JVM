package eventstore
package operations

import NotHandled.{ TooBusy, NotReady }
import Inspection.Decision._
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
      there were noCallsTo(outFunc, inFunc, client)
    }

    "save new OutFunc on connected and retry" in new BaseOperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc mustEqual Some(newOutFunc)
      there were noCallsTo(outFunc, inFunc, client)
      there was one(newOutFunc).apply(pack)
    }

    "replace OutFunc on connected and retry" in new BaseOperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc mustEqual Some(newOutFunc)
      there were noCallsTo(outFunc, inFunc, client)
      there was one(newOutFunc).apply(pack)
    }

    "ignore clientTerminated" in new BaseOperationScope {
      operation.clientTerminated()
      there were noCallsTo(outFunc, inFunc, client)
    }

    "ignore out messages" in new BaseOperationScope {
      operation.inspectOut mustEqual PartialFunction.empty
      operation.inspectOut.isDefinedAt(Ping) must beFalse
      there were noCallsTo(outFunc, inFunc, client)
    }

    "stop on success" in new BaseOperationScope {
      operation.inspectIn(Success(Pong)) must beNone
      thereWasStop(Success(Pong))
    }

    "stop on expected error" in new BaseOperationScope {
      operation.inspectIn(Failure(TestError)) must beNone
      thereWasStop(TestException)
    }

    "retry on NotReady" in new BaseOperationScope {
      operation.inspectIn(Failure(NotHandled(NotReady))) must beSome
      thereWasRetry()
    }

    "retry on TooBusy" in new BaseOperationScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) must beSome
      thereWasRetry()
    }

    "stop on OperationTimedOut" in new BaseOperationScope {
      operation.inspectIn(Failure(OperationTimedOut)) must beNone
      thereWasStop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new BaseOperationScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beNone
      thereWasStop[NotAuthenticatedException]
    }

    "stop on BadRequest" in new BaseOperationScope {
      operation.inspectIn(Failure(BadRequest)) must beNone
      thereWasStop[ServerErrorException]
    }

    "stop on unexpected" in new BaseOperationScope {
      operation.inspectIn(Success(Authenticated))
      thereWasStop[CommandNotExpectedException]
    }

    "stop on unexpected error" in new BaseOperationScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted))
      thereWasStop[CommandNotExpectedException]
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
        case x @ Success(Pong)  => Stop
        case Failure(TestError) => Fail(TestException)
      }
    }

    val operation = BaseOperation(pack, client, inFunc, Some(outFunc), inspection)

    object TestError extends RuntimeException with NoStackTrace
    object TestException extends EsException with NoStackTrace
  }
}