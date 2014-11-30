package eventstore
package operations

import tcp.PackOut
import akka.actor.ActorRef
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.reflect.ClassTag
import scala.util.{ Try, Failure }

trait OperationSpec extends Specification with Mockito {

  protected trait OperationScope extends Scope {
    val inFunc = mock[InFunc]
    val outFunc = mock[OutFunc]
    val client = mock[ActorRef]

    def pack: PackOut

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
  }
}
