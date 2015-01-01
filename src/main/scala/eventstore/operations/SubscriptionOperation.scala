package eventstore
package operations

import akka.actor.ActorRef
import eventstore.NotHandled.{ TooBusy, NotReady }
import eventstore.tcp.PackOut
import eventstore.SubscriptionDropped.AccessDenied
import eventstore.operations.OnIncoming._
import scala.util.{ Success, Failure, Try }

private[eventstore] sealed trait SubscriptionOperation extends Operation {
  def stream: EventStream

  def pack: PackOut

  def inFunc: InFunc

  def id = pack.correlationId

  protected def stop(x: Try[In]): Option[SubscriptionOperation] = {
    inFunc(x)
    None
  }

  protected def unexpected(actual: Any, expectedClass: Class[_]): OnIncoming = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    Stop(new CommandNotExpectedException(msg))
  }

  protected def retry: OnIncoming = Retry(this, pack)

  protected def accessDenied(msg: String): OnIncoming = Stop(new AccessDeniedException(msg))

  object EventAppeared {
    def unapply(x: StreamEventAppeared): Boolean = {
      val streamId = x.event.event.streamId
      stream match {
        case EventStream.All => true
        case `streamId`      => true
        case _               => false
      }
    }
  }
}

private[eventstore] object SubscriptionOperation {

  def apply(
    subscribeTo: SubscribeTo,
    pack: PackOut,
    client: ActorRef,
    inFunc: InFunc,
    outFunc: Option[OutFunc]): Operation = {
    Subscribing(subscribeTo, pack, client, inFunc, outFunc, 0)
  }

  case class Subscribing(
      subscribeTo: SubscribeTo,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: Option[OutFunc],
      version: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectIn(in: Try[In]) = {
      def subscribed = outFunc match {
        case None => Ignore
        case Some(outFunc) =>
          Continue(Subscribed(subscribeTo, pack, client, inFunc, outFunc, version + 1), in)
      }

      def unexpected(x: Any) = this.unexpected(x, Completed.expected)

      in match {
        case Success(Completed())          => subscribed
        case Success(EventAppeared())      => Continue(this, in)
        case Success(x)                    => unexpected(x)
        case Failure(AccessDenied)         => accessDenied(s"Subscription to $stream failed due to access denied")
        case Failure(OperationTimedOut)    => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady)) => retry
        case Failure(NotHandled(TooBusy))  => retry
        case Failure(BadRequest)           => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(NotAuthenticated)     => Stop(NotAuthenticatedException(pack))
        case Failure(x)                    => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe => OnOutgoing.Stop(pack.copy(message = Unsubscribe), Try(Unsubscribed))
    }

    def disconnected = {
      val operation = outFunc.fold(this) { _ => copy(outFunc = None) }
      OnDisconnected.Continue(operation)
    }

    def connected(outFunc: OutFunc) = {
      outFunc(pack)
      Some(copy(outFunc = Some(outFunc)))
    }

    object Completed {
      def unapply(x: SubscribeCompleted): Boolean = (stream, x) match {
        case (EventStream.All, _: SubscribeToAllCompleted) => true
        case (_: EventStream.Id, _: SubscribeToStreamCompleted) => true
        case _ => false
      }

      def expected: Class[_] = stream match {
        case EventStream.All   => classOf[SubscribeToAllCompleted]
        case _: EventStream.Id => classOf[SubscribeToStreamCompleted]
      }
    }
  }

  case class Subscribed(
      subscribeTo: SubscribeTo,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def inspectIn(in: Try[In]): OnIncoming = {
      def unexpected(x: Any) = this.unexpected(x, classOf[StreamEventAppeared])

      in match {
        case Success(EventAppeared()) => Continue(this, in)
        case Success(Unsubscribed)    => Stop(in)
        case Success(x)               => unexpected(x)
        case Failure(AccessDenied)    => accessDenied(s"Subscription on $stream dropped due to access denied")
        case Failure(x)               => unexpected(x)
      }
    }

    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectOut = {
      case Unsubscribe =>
        val pack = this.pack.copy(message = Unsubscribe)
        val operation = Unsubscribing(stream, pack, client, inFunc, outFunc, version + 1)
        OnOutgoing.Continue(operation, pack)
    }

    def disconnected = {
      val operation = Subscribing(subscribeTo, pack, client, inFunc, None, version + 1)
      OnDisconnected.Continue(operation)
    }

    def connected(outFunc: OutFunc) = {
      outFunc(pack)
      Some(Subscribing(subscribeTo, pack, client, inFunc, Some(outFunc), version + 1))
    }
  }

  case class Unsubscribing(
      stream: EventStream,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int) extends SubscriptionOperation {

    def inspectIn(in: Try[In]) = {
      def unexpected(x: Any) = this.unexpected(x, Unsubscribed.getClass)

      in match {
        case Success(Unsubscribed)         => Stop(Unsubscribed)
        case Success(EventAppeared())      => Ignore
        case Success(x)                    => unexpected(x)
        case Failure(AccessDenied)         => accessDenied(s"Unsubscribed from $stream due to access denied")
        case Failure(OperationTimedOut)    => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady)) => retry
        case Failure(NotHandled(TooBusy))  => retry
        case Failure(BadRequest)           => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(x)                    => unexpected(x)
      }
    }

    def clientTerminated = None

    def inspectOut = PartialFunction.empty

    def disconnected = OnDisconnected.Stop(Try(Unsubscribed))

    def connected(outFunc: OutFunc) = stop(Success(Unsubscribed))
  }
}