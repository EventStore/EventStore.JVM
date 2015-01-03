package eventstore
package operations

import eventstore.operations.OnIncoming._
import eventstore.NotHandled.{ NotReady, TooBusy }
import eventstore.tcp.PackOut
import scala.util.control.NoStackTrace
import scala.util.{ Success, Failure }

class BaseOperationSpec extends OperationSpec {
  "BaseOperation" should {
    "return id equal to correlationId" in new BaseOperationScope {
      operation.id mustEqual pack.correlationId
    }

    "drop OutFunc on disconnected" in new BaseOperationScope {
      operation.disconnected mustEqual OnDisconnected.Continue(operation)
    }

    "retry on connected" in new BaseOperationScope {
      operation.connected mustEqual OnConnected.Retry(operation, pack)
    }

    "ignore clientTerminated" in new BaseOperationScope {
      operation.clientTerminated must beNone
    }

    "ignore out messages" in new BaseOperationScope {
      operation.inspectOut mustEqual PartialFunction.empty
      operation.inspectOut.isDefinedAt(Ping) must beFalse
    }

    "stop on success" in new BaseOperationScope {
      operation.inspectIn(Success(Pong)) mustEqual Stop(Pong)
    }

    "stop on expected error" in new BaseOperationScope {
      operation.inspectIn(Failure(TestError)) mustEqual Stop(TestException)
    }

    "retry on NotReady" in new BaseOperationScope {
      val result = operation.inspectIn(Failure(NotHandled(NotReady)))
      result mustEqual Retry(operation, pack)
    }

    "retry on TooBusy" in new BaseOperationScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
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
      operation.disconnected must beLike {
        case OnDisconnected.Continue(o) if o.version == 0 =>
          o.connected must beLike {
            case OnConnected.Retry(o, _) if o.version == 0 => ok
          }
      }
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

    val operation = BaseOperation(pack, client, inspection)

    object TestError extends RuntimeException with NoStackTrace
    object TestException extends EsException with NoStackTrace
  }
}