package eventstore
package examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._


import scala.util.Random
import eventstore.tcp.UuidSerializer

/**
 * @author Yaroslav Klymko
 */
class WriteEventsActor extends Actor with ActorLogging {
  import context.dispatcher

//  val eventId = ByteBuffer.allocate(4).putInt(1)
  def  eventId  = UuidSerializer.serialize(newUuid)

//  def version = Random.nextInt(Int.MaxValue)
//  val writeEvents = WriteEvents(testStreamId, Version(version), List(NewEvent(eventId, Some("ChatMessage"), isJson = false, eventId, None)), requireMaster = false)

  def receive = {
    case x =>
    /*case _: Tcp.Connected =>

      sender ! writeEvents
      val connection = sender
      context.system.scheduler.schedule(1.seconds, 1.seconds, connection, writeEvents)

      context.become {
        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x: WriteEventsCompleted => context.system.scheduler.scheduleOnce(5.seconds, connection, writeEvents)

        case x => log.warning(x.toString)
      }*/
  }
}
