package eventstore
package operations

import scala.util.{Failure, Success, Try}
import eventstore.tcp.PackOut
import NotHandled.{NotReady, TooBusy}
import SubscriptionDropped.AccessDenied
import OnIncoming._

private[eventstore] sealed trait SubscriptionOperation[C] extends Operation[C] {

  def pack: PackOut
  def stream: EventStream

  def id = pack.correlationId

  protected def unexpected(actual: Any, expectedClass: Class[_]): OnIncoming[Operation[C]] = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    Stop(new CommandNotExpectedException(msg))
  }

  protected def retry: OnIncoming[Operation[C]] = Retry(this, pack)
  protected def accessDenied(msg: String): OnIncoming[Operation[C]] = Stop(new AccessDeniedException(msg))
}

private[eventstore] object SubscriptionOperation {

  def apply[C](
    subscribeTo: SubscribeTo,
    pack:        PackOut,
    client:      C,
    ongoing:     Boolean
  ): Operation[C] = {
    Subscribing(subscribeTo, pack, client, ongoing, 0)
  }

  final case class Subscribing[C](
    subscribeTo: SubscribeTo,
    pack:        PackOut,
    client:      C,
    ongoing:     Boolean,
    version:     Int
  ) extends SubscriptionOperation[C] {

    def stream           = subscribeTo.stream
    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectIn(in: Try[In]) = {

      def subscribed =
        if (!ongoing) Ignore
        else Continue(Subscribed(subscribeTo, pack, client, ongoing, version + 1), in)

      def unexpected(x: Any) = this.unexpected(x, Completed.expected)

      in match {
        case Success(Completed())            => subscribed
        case Success(_: StreamEventAppeared) => Continue(this, in)
        case Success(x)                      => unexpected(x)
        case Failure(AccessDenied)           => accessDenied(s"Subscription to $stream failed due to access denied")
        case Failure(OperationTimedOut)      => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))   => retry
        case Failure(NotHandled(TooBusy))    => retry
        case Failure(BadRequest)             => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(NotAuthenticated)       => Stop(NotAuthenticatedException(pack))
        case Failure(x)                      => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe => OnOutgoing.Stop(pack.copy(message = Unsubscribe), Try(Unsubscribed))
    }

    def disconnected = {
      val operation = if (ongoing) copy(ongoing = false) else this
      OnDisconnected.Continue(operation)
    }

    def connected = {
      val operation = if (ongoing) this else copy(ongoing = true)
      OnConnected.Retry(operation, pack)
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

  final case class Subscribed[C](
    subscribeTo: SubscribeTo,
    pack:        PackOut,
    client:      C,
    ongoing:     Boolean,
    version:     Int
  ) extends SubscriptionOperation[C] {

    def stream           = subscribeTo.stream
    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectIn(in: Try[In]): OnIncoming[Operation[C]] = {

      def unexpected(x: Any) = this.unexpected(x, classOf[StreamEventAppeared])

      in match {
        case Success(_: StreamEventAppeared) => Continue(this, in)
        case Success(Unsubscribed)           => Stop(in)
        case Success(x)                      => unexpected(x)
        case Failure(AccessDenied)           => accessDenied(s"Subscription on $stream dropped due to access denied")
        case Failure(x)                      => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe =>
        val pack = this.pack.copy(message = Unsubscribe)
        val operation = Unsubscribing(stream, pack, client, ongoing, version + 1)
        OnOutgoing.Continue(operation, pack)
    }

    def disconnected = {
      val operation = Subscribing(subscribeTo, pack, client, ongoing = false, version + 1)
      OnDisconnected.Continue(operation)
    }

    def connected = {
      val operation = Subscribing(subscribeTo, pack, client, ongoing = true, version + 1)
      OnConnected.Retry(operation, pack)
    }
  }

  final case class Unsubscribing[C](
    stream:  EventStream,
    pack:    PackOut,
    client:  C,
    ongoing: Boolean,
    version: Int
  ) extends SubscriptionOperation[C] {

    def clientTerminated = None
    def inspectOut       = PartialFunction.empty
    def disconnected     = OnDisconnected.Stop(Try(Unsubscribed))
    def connected        = OnConnected.Stop(Try(Unsubscribed))

    def inspectIn(in: Try[In]) = {

      def unexpected(x: Any) = this.unexpected(x, Unsubscribed.getClass)

      in match {
        case Success(Unsubscribed)           => Stop(Unsubscribed)
        case Success(_: StreamEventAppeared) => Ignore
        case Success(x)                      => unexpected(x)
        case Failure(AccessDenied)           => accessDenied(s"Unsubscribed from $stream due to access denied")
        case Failure(OperationTimedOut)      => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))   => retry
        case Failure(NotHandled(TooBusy))    => retry
        case Failure(BadRequest)             => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(x)                      => unexpected(x)
      }
    }
  }
}