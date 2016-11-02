package eventstore.examples

import akka.actor.ActorSystem
import eventstore.{ EventStream, EventNumber, ReadEvent, EsConnection }
import eventstore.tcp.ConnectionActor

class APIsExample {
  val system = ActorSystem()

  def methodCall(): Unit = {
    import system.dispatcher
    val connection = EsConnection(system)
    val future = connection apply ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
  }

  def messageSending(): Unit = {
    val connection = system.actorOf(ConnectionActor.props())
    connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
  }
}