package eventstore

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorLogging, Actor, Identify, ActorIdentity }
import NotHandled.{ TooBusy, NotReady }
import scala.concurrent.duration._

trait AbstractSubscriptionActor[T] extends Actor with ActorLogging {
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def resolveLinkTos: Boolean
  def credentials: Option[UserCredentials]
  def readBatchSize: Int

  import context.dispatcher

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

  val rcvFailureOrUnsubscribe: Receive = rcvFailure orElse {
    case Unsubscribed => context stop self
  }

  val ignoreUnsubscribed: Receive = { case Unsubscribed => }

  def rcvEventAppeared(receive: IndexedEvent => Receive): Receive = {
    case StreamEventAppeared(x) => context become receive(x)
  }

  @deprecated("will migrate into Operation", "2014-11-10")
  def rcvReconnected(receive: => Receive): Receive = {
    case Failure(NotHandled(NotReady | TooBusy)) => // TODO test this use case and move to operation
      val Switch = new {}
      context.system.scheduler.scheduleOnce(100.millis, self, Switch)
      context.become({ case Switch => context become receive })
  }

  @deprecated("will migrate into Operation", "2014-11-10")
  def rcvReconnected(last: Last, next: Next): Receive = rcvReconnected(subscribing(last, next))

  def subscribing(last: Last, next: Next): Receive

  def process(last: Last, events: Seq[T]): Last = events.foldLeft(last)(process)

  def process(last: Last, event: T): Last

  def toClient(event: T) = client ! event

  def toConnection(x: Out) = connection ! credentials.fold[OutLike](x)(x.withCredentials)

  def subscribeToStream() = toConnection(SubscribeTo(streamId, resolveLinkTos = resolveLinkTos))

  def unsubscribe() = toConnection(Unsubscribe)

  // using Identify & ActorIdentity as throttle
  // receiving ActorIdentity means client finished with all previous events
  private val IsReady = Identify("throttle")
  private val Ready = ActorIdentity("throttle", Some(client))

  def rcvReady(receive: => Receive): Receive = {
    case Ready => context become receive
  }

  def checkReadiness() = client ! IsReady

  def whenReady(receive: => Receive, ready: Boolean) = {
    checkReadiness()
    if (ready) receive
    else rcvFailure orElse rcvReady(receive)
  }
}

case object LiveProcessingStarted {
  /**
   * Java API
   */
  def getInstance = this
}