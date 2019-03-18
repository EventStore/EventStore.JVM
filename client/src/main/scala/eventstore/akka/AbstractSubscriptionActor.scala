package eventstore
package akka

import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, Identify}

private[eventstore] trait AbstractSubscriptionActor[T] extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def credentials: Option[UserCredentials]
  def settings: Settings

  type Next
  type Last

  context watch client
  context watch connection

  val rcvFailure: Receive = {
    case failure @ Failure(e) =>
      log.error(e.toString)
      client ! failure
      context stop self
  }

  val rcvFailureOrUnsubscribe: Receive = rcvFailure or {
    case Unsubscribed => context stop self
  }

  val ignoreUnsubscribed: Receive = { case Unsubscribed => }

  def rcvEventAppeared(receive: IndexedEvent => Receive): Receive = {
    case StreamEventAppeared(x) => context become receive(x)
  }

  def subscribing(last: Last, next: Next): Receive

  def process(last: Last, events: Seq[T]): Last = events.foldLeft(last)(process)

  def process(last: Last, event: T): Last

  def toClient(event: T) = client ! event

  def toConnection(x: Out) = connection ! credentials.fold[OutLike](x)(x.withCredentials)

  def subscribeToStream() = toConnection(SubscribeTo(streamId, resolveLinkTos = settings.resolveLinkTos))

  def unsubscribe() = toConnection(Unsubscribe)

  // using Identify & ActorIdentity as throttle
  // receiving ActorIdentity means client finished with all previous events
  private val IsReady = Identify("throttle")
  private val Ready = ActorIdentity("throttle", Some(client))

  def rcvReady(receive: => Receive): Receive = {
    case Ready => context become receive
  }

  def checkReadiness() = client ! IsReady

  def whenReady(receive: => Receive, ready: Boolean): Receive = {
    checkReadiness()
    if (ready) receive
    else rcvFailure or rcvReady(receive)
  }
}