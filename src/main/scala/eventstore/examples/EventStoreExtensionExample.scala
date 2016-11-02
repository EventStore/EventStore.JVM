package eventstore.examples

import eventstore.{ EventStream, ReadEvent, EventStoreExtension }
import akka.actor.ActorSystem

object EventStoreExtensionExample {
  val system = ActorSystem()
  import system.dispatcher

  EventStoreExtension(system).actor ! ReadEvent(EventStream.Id("stream"))
  EventStoreExtension(system).connection(ReadEvent(EventStream.Id("stream")))
}
