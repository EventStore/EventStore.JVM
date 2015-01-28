package eventstore

import akka.actor.Status.Failure
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

class EventStoreExtensionSpec extends util.ActorSpec {
  Properties.setProp("eventstore.address.port", "0")

  val readEvent = ReadEvent(EventStream.Id(randomUuid.toString))

  "EventStoreExtension" should {
    "return connection actor" in new ActorScope {
      EventStoreExtension(system).actor ! readEvent
      expectMsgPF() { case Failure(_: CannotEstablishConnectionException) => }
    }

    "return connection instance" in new ActorScope {
      val future = EventStoreExtension(system).connection.future(readEvent)
      Await.result(future, 3.seconds) must throwAn[CannotEstablishConnectionException]
    }
  }
}
