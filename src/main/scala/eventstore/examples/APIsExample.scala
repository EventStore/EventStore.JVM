package eventstore.examples

import akka.actor.ActorSystem
import eventstore.{ EventStream, EventNumber, ReadEvent, EsConnection }
import eventstore.tcp.ConnectionActor

class APIsExample {
  val system = ActorSystem()

  def methodCall() {
    val connection = EsConnection(system)
    val future = connection future ReadEvent(EventStream("my-stream"), EventNumber.First)
  }

  def messageSending() {
    val connection = system.actorOf(ConnectionActor.props())
    connection ! ReadEvent(EventStream("my-stream"), EventNumber.First)
  }
}