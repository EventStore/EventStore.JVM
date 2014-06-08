package eventstore.examples

import eventstore.{ EventStream, ReadEvent, EventStoreExtension }
import akka.actor.ActorSystem

object EventStoreExtensionExample {
  val system = ActorSystem()

  EventStoreExtension(system).actor ! ReadEvent(EventStream("stream"))
  EventStoreExtension(system).connection.future(ReadEvent(EventStream("stream")))
}
