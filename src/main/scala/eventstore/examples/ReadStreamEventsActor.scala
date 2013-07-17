package eventstore.examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._
import eventstore._
import eventstore.ReadStreamEventsCompleted
import eventstore.ReadStreamEvents

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsActor extends Actor with ActorLogging {
  import context.dispatcher

  val readStreamEvents = ReadStreamEvents(
    streamId = "chat-GeneralChat",
    fromEventNumber = 0,
    maxCount = 10000,
    resolveLinkTos = false,
    direction = ReadDirection.Forward)

  def receive = {
    case _: Tcp.Connected =>

      sender ! readStreamEvents
      sender ! readStreamEvents.copy(streamId = "notfound")

      context.become {
        case x: ReadStreamEventsCompleted => context.system.scheduler.scheduleOnce(5.seconds, sender, readStreamEvents)

        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x => log.warning(x.toString)
      }
  }
}
