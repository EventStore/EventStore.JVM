package eventstore
package examples

import akka.actor.{ActorRef, Props, ActorLogging, Actor}

/**
 * @author Yaroslav Klymko
 */
class SubscribeActor(connection: ActorRef) extends Actor with ActorLogging {
  val subscribeToStream = SubscribeTo(AllStreams, resolveLinkTos = false)
  val stats = context.actorOf(Props[MessagesPerSecondActor])

  override def preStart() {
    connection ! subscribeToStream
  }

  def receive = {
    case x: StreamEventAppeared => stats ! x
    case SubscriptionDropped => sender ! subscribeToStream
  }
}