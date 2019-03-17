package eventstore
package akka

import scala.concurrent.duration._
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.ActorRef
import _root_.akka.testkit._
import org.specs2.mock.Mockito

abstract class AbstractSubscriptionActorSpec extends ActorSpec with Mockito {

  abstract class AbstractScope extends ActorScope {
    val duration = 1.second
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()

    val actor = createActor()
    watch(actor)

    def createActor(): ActorRef

    def streamId: EventStream

    def expectNoActivity(): Unit = {
      expectNoMessage(duration)
      connection.expectNoMessage(duration)
    }

    def streamEventAppeared(x: Event) = StreamEventAppeared(IndexedEvent(x, Position.Exact(x.number.value)))

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def expectActorTerminated(testKit: TestKitBase = this): Unit = {
      testKit.expectTerminated(actor)
    }

    def expectTerminatedOnFailure(): Unit = {
      val failure = Failure(new ServerErrorException("test"))
      actor ! failure
      expectMsg(failure)
      expectTerminated(actor)
      val duration = 1.seconds
      expectNoMessage(duration)
      connection.expectNoMessage(duration)
    }

    def notHandled(x: NotHandled.Reason) = Failure(NotHandled(x))

    def credentials: Option[UserCredentials] = None
  }
}
