package eventstore.examples

import akka.actor._
import eventstore.Subscription.LiveProcessingStarted
import eventstore.tcp.ConnectionActor
import eventstore.{ Event, EventStream, StreamSubscriptionActor }
import scala.concurrent.duration._

object CountStream extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countStream = system.actorOf(Props[CountAll], "count-stream")
  system.actorOf(StreamSubscriptionActor.props(connection, countStream, EventStream("chat-GeneralChat")), "subscription")
}

class CountStream extends Actor with ActorLogging {
  context.setReceiveTimeout(5.seconds)

  def receive = count(0)

  def count(n: Long): Receive = {
    case x: Event              => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout        => log.info("count {}", n)
  }
}