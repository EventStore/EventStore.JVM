package eventstore.examples

import scala.concurrent.duration._
import java.net.InetSocketAddress

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import eventstore.{ EventRecord, EventStream, LiveProcessingStarted, PersistentSubscriptionActor, Settings, UserCredentials }
import eventstore.tcp.ConnectionActor

object PersistentSubscriptionExample extends App {
  val system = ActorSystem()

  val settings = Settings(
    address = new InetSocketAddress("127.0.0.1", 1113)
  )

  val actor = system.actorOf(Props[CountPersistentStream])

  val connection = system.actorOf(ConnectionActor.props(settings))

  val sub = system.actorOf(PersistentSubscriptionActor.props(connection, actor, EventStream.Id("stream"),
    "stream-group", None, settings))
}

class CountPersistentStream extends Actor with ActorLogging {
  context.setReceiveTimeout(1.second)

  def receive: Receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case x: EventRecord =>
      log.info("count {}", n)
      context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
  }
}