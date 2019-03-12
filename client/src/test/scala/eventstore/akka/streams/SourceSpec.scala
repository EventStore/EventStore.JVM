package eventstore
package akka
package streams

import scala.concurrent.duration._
import _root_.akka.NotUsed
import _root_.akka.stream.ActorMaterializer
import _root_.akka.stream.scaladsl._
import _root_.akka.stream.testkit.scaladsl._
import _root_.akka.testkit._
import org.specs2.mock.Mockito

abstract class SourceSpec extends ActorSpec with Mockito {

  abstract class AbstractSourceScope[T] extends ActorScope {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val duration = 1.second
    val readBatchSize = 2
    val resolveLinkTos = false
    val connection = TestProbe()
    def streamId: EventStream

    val probe = createSource()
      .runWith(TestSink.probe[T])
      .ensureSubscription()

    def createSource(): Source[T, NotUsed]
    def newEvent(x: Long): T

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)
    val failure = new ServerErrorException("test")

    ///

    def expectError(t: Throwable): Unit =
      probe.expectError(t)

    def expectNoActivity(): Unit = {
      expectNoEvent()
      connection.expectNoMessage(duration)
    }

    def expectNoEvent(): Unit =
      probe.request(1).expectNoMessage(duration)

    def expectComplete(): Unit =
      probe.request(1).expectComplete()

    def expectEvent(x: T): Unit =
      probe.requestNext(x)

    def subscribeTo = SubscribeTo(streamId, resolveLinkTos = resolveLinkTos)
    def credentials: Option[UserCredentials] = None
    def infinite = true

  }

}