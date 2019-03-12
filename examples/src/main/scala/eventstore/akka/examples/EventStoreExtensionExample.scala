package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem

object EventStoreExtensionExample {
  val system = ActorSystem()
  import system.dispatcher

  EventStoreExtension(system).actor ! ReadEvent(EventStream.Id("stream"))
  EventStoreExtension(system).connection(ReadEvent(EventStream.Id("stream")))
}
