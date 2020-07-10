package eventstore
package core
package operations

import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}
import util.uuid.randomUuid
import tcp.PackOut
import NotHandled.{IsReadOnly, NotReady, TooBusy}
import OnIncoming._

class SimpleOperationSpec extends OperationSpec {

  "SimpleOperation" should {

    "return id equal to correlationId" in new SimpleOperationScope {
      operation.id mustEqual pack.correlationId
    }

    "drop OutFunc on disconnected" in new SimpleOperationScope {
      operation.disconnected mustEqual OnDisconnected.Continue(operation)
    }

    "retry on connected" in new SimpleOperationScope {
      operation.connected mustEqual OnConnected.Retry(operation, pack)
    }

    "ignore clientTerminated" in new SimpleOperationScope {
      operation.clientTerminated must beNone
    }

    "ignore out messages" in new SimpleOperationScope {
      operation.inspectOut mustEqual PartialFunction.empty
      operation.inspectOut.isDefinedAt(Ping) must beFalse
    }

    "stop on success" in new SimpleOperationScope {
      operation.inspectIn(Success(Pong)) mustEqual Stop(Pong)
    }

    "stop on expected error" in new SimpleOperationScope {
      operation.inspectIn(Failure(TestError)) mustEqual Stop(TestException)
    }

    "retry on NotReady" in new SimpleOperationScope {
      val result = operation.inspectIn(Failure(NotHandled(NotReady)))
      result mustEqual Retry(operation, pack)
    }

    "retry on TooBusy" in new SimpleOperationScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
    }

    "stop on IsReadOnly" in new SimpleOperationScope {
      operation.inspectIn(Failure(NotHandled(IsReadOnly))) mustEqual(
        Stop(Failure(ServerErrorException(s"${pack.message} is not supported. Node is Read Only")))
      )
    }

    "stop on OperationTimedOut" in new SimpleOperationScope {
      operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new SimpleOperationScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beLike {
        case Stop(Failure(_: NotAuthenticatedException)) => ok
      }
    }

    "stop on BadRequest" in new SimpleOperationScope {
      operation.inspectIn(Failure(BadRequest)) must beLike {
        case Stop(Failure(_: ServerErrorException)) => ok
      }
    }

    "stop on unexpected" in new SimpleOperationScope {
      operation.inspectIn(Success(Authenticated)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected error" in new SimpleOperationScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "always return 0 for version" in new SimpleOperationScope {
      operation.version mustEqual 0
      operation.disconnected must beLike {
        case OnDisconnected.Continue(o) if o.version == 0 =>
          o.connected must beLike {
            case OnConnected.Retry(o, _) if o.version == 0 => ok
          }
      }
    }
  }

  trait SimpleOperationScope extends OperationScope {
    val pack = PackOut(Ping, randomUuid)
    val inspection = new Inspection {
      def expected = Pong.getClass
      def pf = {
        case _@ Success(Pong)  => Inspection.Decision.Stop
        case Failure(TestError) => Inspection.Decision.Fail(TestException)
      }
    }

    val operation = SimpleOperation(pack, client, inspection)

    object TestError extends RuntimeException with NoStackTrace
    object TestException extends EsException("test")
  }
}