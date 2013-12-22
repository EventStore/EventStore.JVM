package eventstore
package examples

import akka.actor.{ ActorLogging, Actor }
import akka.io.Tcp
import scala.concurrent.duration._

class ReadAllEventsActor extends Actor with ActorLogging {

  val readAllEvents = ReadAllEvents(Position.First, 10000, ReadDirection.Forward)

  import context.dispatcher

  def receive = {
    case _: Tcp.Connected =>

      sender ! readAllEvents

      context.become {
        case HeartbeatRequest => sender ! HeartbeatResponse
        case x: ReadAllEventsCompleted =>
          //          sender ! readAllEvents
          context.system.scheduler.scheduleOnce(5.seconds, sender, readAllEvents)
        //        case x: SubscriptionConfirmation =>
        case x => log.warning(x.toString)
      }
  }
}
