package eventstore
package examples

import akka.actor.{ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class SubscribeActor extends Actor with ActorLogging {

  import context.dispatcher

  val subscribeToStream = SubscribeTo(EventStream.Id("test"), resolveLinkTos = false)

  sender ! subscribeToStream


  def receive = {
    case _: Tcp.Connected =>


      context.become {
        case x: StreamEventAppeared =>

        case SubscriptionDropped => sender ! subscribeToStream

        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x => log.warning(x.toString)
      }
  }
}