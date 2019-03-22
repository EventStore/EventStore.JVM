package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem
import eventstore.akka.tcp.ConnectionActor

class APIsExample {
  val system = ActorSystem()

  def methodCall(): Unit = {
    val connection = EsConnection(system)
    val _ = connection apply ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
  }

  def messageSending(): Unit = {
    val connection = system.actorOf(ConnectionActor.props())
    connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
  }
}