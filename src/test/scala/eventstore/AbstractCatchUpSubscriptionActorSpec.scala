package eventstore

import akka.actor.{ Props, SupervisorStrategy, Actor }
import akka.testkit._
import org.specs2.mock.Mockito
import scala.concurrent.duration._

abstract class AbstractCatchUpSubscriptionActorSpec extends util.ActorSpec with Mockito {

  abstract class AbstractScope extends ActorScope {
    val duration = 1.second
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()

    class Supervisor extends Actor {
      override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
      def receive = PartialFunction.empty
    }

    val actor = {
      val supervisor = TestActorRef(new Supervisor)
      val actor = TestActorRef(props, supervisor, "test")
      watch(actor)
      actor
    }

    def props: Props

    def streamId: EventStream

    def expectNoActivity {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    def streamEventAppeared(x: Event) = StreamEventAppeared(IndexedEvent(x, Position(x.number.value)))

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def expectActorTerminated(testKit: TestKitBase = this) {
      testKit.expectTerminated(actor)
      actor.underlying.isTerminated must beTrue
      expectNoActivity
    }
  }

}
