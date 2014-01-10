package eventstore.examples

import akka.actor._
import eventstore.Subscription.LiveProcessingStarted
import eventstore.tcp.ConnectionActor
import eventstore.{ IndexedEvent, SubscriptionActor }
import scala.concurrent.duration._

object CountAll extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countAll = system.actorOf(Props[CountAll], "count-all")
  system.actorOf(SubscriptionActor.props(connection, countAll), "subscription")
}

class CountAll extends Actor with ActorLogging {
  context.setReceiveTimeout(5.seconds)

  def receive = count(0)

  def count(n: Long): Receive = {
    case x: IndexedEvent       => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout        => log.info("count {}", n)
  }
}