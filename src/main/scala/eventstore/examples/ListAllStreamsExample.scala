package eventstore.examples

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import eventstore.{ EventStoreExtension, EventStream }

object ListAllStreamsExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()
  val connection = EventStoreExtension(system).connection
  val publisher = connection.streamPublisher(EventStream.System.`$streams`, infinite = false, resolveLinkTos = true)
  Source(publisher)
    .runForeach { x => println(x.streamId.streamId) }
    .onComplete { _ => system.terminate() }
}
