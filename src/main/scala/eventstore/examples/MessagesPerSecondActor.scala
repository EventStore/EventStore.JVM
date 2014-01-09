package eventstore.examples

import akka.actor.{ ActorLogging, Actor }
import scala.concurrent.duration._

class MessagesPerSecondActor extends Actor with ActorLogging {

  import context.dispatcher

  var count = 0
  var time = currentTimeMillis

  context.system.scheduler.schedule(5.seconds, 5.seconds, self, Report)

  def receive = {
    case Report if count > 0 =>
      val current = currentTimeMillis
      val value = count / ((current - time) / 1000)
      if (value > 0) log.info("{} m/s", value)
      time = current
      count = 0

    case x => count = count + 1
  }

  case object Report

  def currentTimeMillis = System.currentTimeMillis()
}
