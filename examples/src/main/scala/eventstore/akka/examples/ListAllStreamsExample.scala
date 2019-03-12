package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem
import _root_.akka.stream.ActorMaterializer

object ListAllStreamsExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()
  val connection = EventStoreExtension(system).connection
  val source = connection.streamSource(EventStream.System.`$streams`, infinite = false, resolveLinkTos = true)

  source
    .runForeach { x => println(x.streamId.streamId) }
    .onComplete { _ => system.terminate() }
}
