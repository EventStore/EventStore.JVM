package eventstore
package akka

import scala.concurrent.duration._
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.ActorRef
import _root_.akka.testkit._
import eventstore.akka.Settings.{Default => DS}

abstract class AbstractSubscriptionActorSpec extends ActorSpec {

  abstract class AbstractScope extends ActorScope {

    val duration: FiniteDuration             = 250.millis
    val readBatchSize: Int                   = 10
    val resolveLinkTos: Boolean              = false
    val connection: TestProbe                = TestProbe()
    val actor: ActorRef                      = watch(createActor())
    def settings: Settings                   = DS.copy(resolveLinkTos = resolveLinkTos, readBatchSize = readBatchSize)
    def credentials: Option[UserCredentials] = None

    def createActor(): ActorRef
    def streamId: EventStream

    def expectNoActivity(): Unit = {
      expectNoMessage(duration)
      connection.expectNoMessage(duration)
    }

    def streamEventAppeared(x: Event): StreamEventAppeared =
      StreamEventAppeared(IndexedEvent(x, Position.Exact(x.number.value)))

    def subscribeTo: SubscribeTo =
      SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)

    def expectActorTerminated(testKit: TestKitBase = this): Unit = {
      testKit.expectTerminated(actor)
    }

    def expectTerminatedOnFailure(): Unit = {
      val failure = Failure(new ServerErrorException("test"))
      connection.reply(failure)
      expectMsg(failure)
      expectTerminated(actor)
      expectNoMessage(duration)
      connection.expectNoMessage(duration)
    }
  }
}
