package eventstore
package examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsActor extends Actor with ActorLogging {

  val readAllEvents = ReadAllEvents(0, 0, 10000, resolveLinkTos = true, ReadDirection.Forward)

  import context.dispatcher

  def receive = {
    case _: Tcp.Connected =>

      sender ! readAllEvents

      context.become {
        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand
        case x: ReadAllEventsCompleted =>
//          sender ! readAllEvents
          context.system.scheduler.scheduleOnce(5.seconds, sender, readAllEvents)
        //        case x: SubscriptionConfirmation =>
        case x => log.warning(x.toString)
      }
  }
}
