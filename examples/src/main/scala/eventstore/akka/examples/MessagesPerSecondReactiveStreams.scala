package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem
import _root_.akka.stream.scaladsl._
import org.reactivestreams.{Publisher, Subscriber}
import scala.concurrent.duration._

object MessagesPerSecondReactiveStreams extends App {
  implicit val system = ActorSystem()
  val connection = EventStoreExtension(system).connection

  val publisher: Publisher[String] = connection.allStreamsSource()
    .groupedWithin(Int.MaxValue, 1.second)
    .map { xs => f"${xs.size.toDouble / 1000}%2.1fk m/s" }
    .runWith(Sink.asPublisher(fanout = false))

  val subscriber: Subscriber[String] = Source.asSubscriber[String]
    .to(Sink.foreach(println))
    .run()

  publisher.subscribe(subscriber)
}
