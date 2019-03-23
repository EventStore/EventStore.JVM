package eventstore
package akka
package examples

import _root_.akka.actor._
import scala.concurrent.duration._
import eventstore.akka.tcp.ConnectionActor

object CountStream extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countStream = system.actorOf(Props[CountStream], "count-stream")
  val props = StreamSubscriptionActor.props(
    connection,
    countStream,
    EventStream.Id("chat-GeneralChat"),
    None,
    None,
    Settings.Default
  )
  system.actorOf(props, "subscription")
}

class CountStream extends Actor with ActorLogging {
  context.setReceiveTimeout(1.second)

  def receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case _: Event              => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout if !printed =>
      log.info("count {}", n)
      context become count(n, printed = true)
  }
}