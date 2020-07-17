package eventstore
package akka
package examples

import _root_.akka.actor._
import scala.concurrent.duration._
import eventstore.akka.tcp.ConnectionActor

object MessagesPerSecond extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())
  val messagePerSecond = system.actorOf(Props[MessagesPerSecond](), "messages-per-second")
  system.actorOf(SubscriptionActor.props(connection, messagePerSecond, None, None, Settings.Default))
}

class MessagesPerSecond extends Actor with ActorLogging {

  import context.dispatcher

  context.setReceiveTimeout(2.seconds)

  override def receive: Receive = receive(0, Nil, scheduled = false)

  def receive(n: Long, ns: List[Long], scheduled: Boolean): Receive = {
    case _: IndexedEvent =>
      if (!scheduled) context.system.scheduler.scheduleOnce(1.second, self, Tick)
      context become receive(n + 1, ns, scheduled = true)

    case Tick =>
      val (x, xs) =
        if (n <= 100) (n, ns)
        else {
          log.info(ms(n))
          (0L, n :: ns)
        }
      context become receive(x, xs, scheduled = false)

    case ReceiveTimeout if ns.nonEmpty =>
      log.info("{} in average", ms(ns.sum / ns.size))
      context become receive
  }

  def ms(x: Long) = f"${x.toDouble / 1000}%2.1fk m/s"

  case object Tick
}