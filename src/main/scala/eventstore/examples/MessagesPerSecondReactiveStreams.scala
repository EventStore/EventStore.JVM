package eventstore.examples

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import eventstore.EventStoreExtension
import org.reactivestreams.{Publisher, Subscriber}
import scala.concurrent.duration._

object MessagesPerSecondReactiveStreams extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
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
