package eventstore

import akka.actor.{Props, ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._
import eventstore.examples.MessagesPerSecondActor

/**
 * @author Yaroslav Klymko
 */
class SubscribeActor extends Actor with ActorLogging {

  import context.dispatcher

  //  val subscribeToStream = SubscribeToStream(testStreamId, resolveLinkTos = false)
  val subscribeToStream = SubscribeTo(Stream.All, resolveLinkTos = false)


  val stats = context.actorOf(Props[MessagesPerSecondActor])

  def receive = {
    case _: Tcp.Connected =>

      sender ! subscribeToStream

      context.become({
        case x: SubscribeToAllCompleted =>


        case x: StreamEventAppeared => stats ! x

        case SubscriptionDropped =>
          println(SubscriptionDropped)
          sender ! subscribeToStream

        case HeartbeatRequestCommand => sender ! HeartbeatResponseCommand

        case x => log.warning(x.toString)
      }, discardOld = false)
  }
}