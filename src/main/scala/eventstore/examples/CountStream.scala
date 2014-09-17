package eventstore.examples

import akka.actor._
import eventstore.LiveProcessingStarted
import eventstore.tcp.ConnectionActor
import eventstore.{ Event, EventStream, StreamSubscriptionActor }
import scala.concurrent.duration._

object CountStream extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countStream = system.actorOf(Props[CountStream], "count-stream")
  system.actorOf(StreamSubscriptionActor.props(connection, countStream, EventStream.Id("chat-GeneralChat")), "subscription")
}

class CountStream extends Actor with ActorLogging {
  context.setReceiveTimeout(1.second)

  def receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case x: Event              => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout if !printed =>
      log.info("count {}", n)
      context become count(n, printed = true)
  }
}