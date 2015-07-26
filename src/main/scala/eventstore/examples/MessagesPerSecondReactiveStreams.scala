package eventstore.examples

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import eventstore.EventStoreExtension

import scala.concurrent.duration._

object MessagesPerSecondReactiveStreams extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val connection = EventStoreExtension(system).connection
  val publisher = connection.allStreamsPublisher()

  Source(publisher)
    .groupedWithin(Int.MaxValue, 1.second)
    .runForeach { xs => println(f"${xs.size.toDouble / 1000}%2.1fk m/s") }
}
