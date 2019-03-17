package eventstore
package akka

import _root_.akka.actor.Status.Failure
import scala.concurrent.Await
import scala.concurrent.duration._

class EventStoreExtensionSpec extends ActorSpec {
  val readEvent = ReadEvent(EventStream.Id(randomUuid.toString))

  "EventStoreExtension" should {
    "return connection actor" in new ActorScope {
      EventStoreExtension(system).actor ! readEvent
      expectMsgPF() { case Failure(_: EsException) => }
    }

    "return connection instance" in new ActorScope {
      val future = EventStoreExtension(system).connection(readEvent)
      Await.result(future, 3.seconds) must throwAn[EsException]
    }
  }
}
