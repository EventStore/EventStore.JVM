package eventstore
package util

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl._
import akka.testkit._
import org.specs2.mock.Mockito
import scala.concurrent.duration._

abstract class SourceSpec extends util.ActorSpec with Mockito {

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