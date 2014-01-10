package eventstore

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit._
import org.specs2.mock.Mockito
import scala.concurrent.duration._
import eventstore.tcp.ConnectionActor.WaitReconnected

abstract class AbstractSubscriptionActorSpec extends util.ActorSpec with Mockito {

  abstract class AbstractScope extends ActorScope {
    val duration = 1.second
    val readBatchSize = 10
    val resolveLinkTos = false
    val connection = TestProbe()

    val actor = TestActorRef(props)
    watch(actor)

    def props: Props

    def streamId: EventStream

    def expectNoActivity() {
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    def streamEventAppeared(x: Event) = StreamEventAppeared(IndexedEvent(x, Position(x.number.value)))

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def expectActorTerminated(testKit: TestKitBase = this) {
      testKit.expectTerminated(actor)
    }

    def expectTerminatedOnFailure(expectUnsubscribe: Boolean = false) {
      actor ! Failure(EsException(EsError.Error))
      if (expectUnsubscribe) connection expectMsg Unsubscribe
      expectTerminated(actor)
      val duration = 1.seconds
      expectNoMsg(duration)
      connection.expectNoMsg(duration)
    }

    def expectWaitReconnected() {
      actor ! Failure(EsException(EsError.ConnectionLost))
      connection.expectMsg(WaitReconnected)
    }
  }
}
