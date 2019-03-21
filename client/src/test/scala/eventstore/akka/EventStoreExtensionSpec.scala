package eventstore
package akka

import _root_.akka.actor.Status.Failure
import _root_.akka.pattern.AskTimeoutException

class EventStoreExtensionSpec extends ActorSpec {

  val extension = EventStoreExtension(system)
  val readEvent = ReadEvent(EventStream.Id(randomUuid.toString))

  "EventStoreExtension" should {
    "return connection actor" in new ActorScope {
      extension.actor ! readEvent
      expectMsgPF() { case Failure(_: EsException) => }
    }

    "return connection instance" in {
      extension.connection(readEvent).await_ must throwAn[EsException] or throwAn[AskTimeoutException]
    }
  }
}
