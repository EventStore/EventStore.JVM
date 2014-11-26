package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import Inspection.Decision._
import tcp.PackOut
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace
import scala.util.{ Success, Try, Failure }

class BaseOperationSpec extends Specification with Mockito {
  "BaseOperation" should {
    "return id equal to correlationId" in new OperationScope {
      operation.id mustEqual pack.correlationId
    }

    "drop OutFunc on connectionLost" in new OperationScope {
      val actual = operation.connectionLost()
      actual must beSome
      actual.get.outFunc must beNone
      there were noCallsTo(outFunc, inFunc, client)
    }

    "save new OutFunc on connected and retry" in new OperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc must beSome(newOutFunc)
      there were noCallsTo(outFunc, inFunc, client)
      there was one(newOutFunc).apply(pack)
    }

    "replace OutFunc on connected and retry" in new OperationScope {
      val newOutFunc = mock[OutFunc]
      val actual = operation.copy(outFunc = None).connected(newOutFunc)
      actual must beSome
      actual.get.outFunc must beSome(newOutFunc)
      there were noCallsTo(outFunc, inFunc, client)
      there was one(newOutFunc).apply(pack)
    }

    "ignore clientTerminated" in new OperationScope {
      operation.clientTerminated()
      there were noCallsTo(outFunc, inFunc, client)
    }

    "ignore out messages" in new OperationScope {
      operation.inspectOut mustEqual PartialFunction.empty
      (operation: Operation).inspectOut(Ping) must throwA[MatchError]
      there were noCallsTo(outFunc, inFunc, client)
    }

    "stop on success" in new OperationScope {
      operation.inspectIn(Success(Pong)) must beNone
      thereWasStop(Success(Pong))
    }

    "stop on expected error" in new OperationScope {
      operation.inspectIn(Failure(TestError)) must beNone
      thereWasStop(TestException)
    }

    "retry on NotReady" in new OperationScope {
      operation.inspectIn(Failure(NotHandled(NotReady))) must beSome
      thereWasRetry()
    }

    "retry on TooBusy" in new OperationScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) must beSome
      thereWasRetry()
    }

    "stop on OperationTimedOut" in new OperationScope {
      operation.inspectIn(Failure(OperationTimedOut)) must beNone
      thereWasStop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new OperationScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beNone
      thereWasStop[NotAuthenticatedException]
    }

    "stop on BadRequest" in new OperationScope {
      operation.inspectIn(Failure(BadRequest)) must beNone
      thereWasStop[ServerErrorException]
    }

    "stop on unexpected" in new OperationScope {
      operation.inspectIn(Success(Authenticated))
      thereWasStop[CommandNotExpectedException]
    }

    "stop on unexpected error" in new OperationScope {
      operation.inspectIn(Success(Authenticated))
      thereWasStop[CommandNotExpectedException]
    }

    "always return 0 for version" in new OperationScope {
      operation.version mustEqual 0
      val o1 = operation.connectionLost().get
      o1.version mustEqual 0
      val o2 = operation.connected(outFunc).get
      o2.version mustEqual 0
    }
  }

  private trait OperationScope extends Scope {
    val inFunc = mock[InFunc]
    val outFunc = mock[OutFunc]
    val pack = PackOut(Ping)
    val inspection = new Inspection {
      def expected = Pong.getClass
      def pf = {
        case x @ Success(Pong)  => Stop
        case Failure(TestError) => Fail(TestException)
      }
    }

    val client = mock[ActorRef]
    val operation = BaseOperation(pack, client, inFunc, Some(outFunc), inspection)

    def failureType[T](implicit tag: ClassTag[T]) = {
      def likeFailure(x: Try[In]) = x must beLike {
        case Failure(tag(_)) => ok
      }
      argThat(likeFailure _)
    }

    def thereWasRetry() = {
      there was one(outFunc).apply(pack)
      there were noCallsTo(inFunc, client)
    }

    def thereWasStop[T: ClassTag] = {
      there was one(inFunc).apply(failureType[T])
      there were noCallsTo(outFunc, client)
    }

    def thereWasStop(x: EsException): Unit = {
      thereWasStop(Failure(x))
    }

    def thereWasStop(x: Try[In]): Unit = {
      there was one(inFunc).apply(x)
      there were noCallsTo(outFunc, client)
    }

    object TestError extends RuntimeException with NoStackTrace
    object TestException extends EsException with NoStackTrace
  }
}