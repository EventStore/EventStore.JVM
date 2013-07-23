package eventstore

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.io.Tcp
import scala.concurrent.duration._
import examples.MessagesPerSecondActor

/**
 * @author Yaroslav Klymko
 */
class SubscribeActor(connection: ActorRef) extends Actor with ActorLogging {

  //  import context.dispatcher

  //  val subscribeToStream = SubscribeToStream(testStreamId, resolveLinkTos = false)
  val subscribeToStream = SubscribeTo(EventStream.All, resolveLinkTos = false)

  val stats = context.actorOf(Props[MessagesPerSecondActor])

  def receive = {
    case x: SubscribeToAllCompleted =>

    case x: StreamEventAppeared => stats ! x

    case SubscriptionDropped =>
      println(SubscriptionDropped)
      sender ! subscribeToStream
  }
}