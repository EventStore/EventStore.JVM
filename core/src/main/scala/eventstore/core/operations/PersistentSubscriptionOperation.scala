package eventstore
package core
package operations

import scala.util.{Failure, Success, Try}
import eventstore.core.tcp.PackOut
import NotHandled.{ NotReady, TooBusy }
import PersistentSubscription.{ Ack, Connect, EventAppeared, Nak }
import SubscriptionDropped._
import OnIncoming._

private[eventstore] sealed trait PersistentSubscriptionOperation[C] extends Operation[C] {
  def stream: EventStream

  def pack: PackOut

  def id = pack.correlationId

  protected def unexpected(actual: Any, expectedClass: Class[_]): OnIncoming[Operation[C]] = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    Stop(new CommandNotExpectedException(msg))
  }

  protected def retry: OnIncoming[Operation[C]] = Retry(this, pack)

  protected def accessDenied(msg: String): OnIncoming[Operation[C]] = Stop(new AccessDeniedException(msg))
}

private[eventstore] object PersistentSubscriptionOperation {

  def apply[C](
    connect: Connect,
    pack:    PackOut,
    client:  C,
    ongoing: Boolean
  ): Operation[C] = {
    Connecting(connect, pack, client, ongoing, 0)
  }

  final case class Connecting[C](
      connect: Connect,
      pack:    PackOut,
      client:  C,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation[C] {

    def stream = connect.streamId

    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectIn(in: Try[In]) = {
      def subscribed =
        if (!ongoing) Ignore
        else Continue(Connected(connect, pack, client, ongoing, version + 1), in)

      def unexpected(x: Any) = this.unexpected(x, classOf[Connected[C]])

      in match {
        case Success(Completed())          => subscribed
        case Success(_: EventAppeared)     => Continue(this, in)
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
      val operation = if (ongoing) copy(ongoing = false) else this
      OnDisconnected.Continue(operation)
    }

    def connected = {
      val operation = if (ongoing) this else copy(ongoing = true)
      OnConnected.Retry(operation, pack)
    }

    object Completed {
      def unapply(x: PersistentSubscription.Connected): Boolean = (stream, x) match {
        case (_, _: PersistentSubscription.Connected) => true
        case _                                        => false
      }
    }
  }

  final case class Connected[C](
      connect: Connect,
      pack:    PackOut,
      client:  C,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation[C] {

    def stream = connect.streamId

    def inspectIn(in: Try[In]): OnIncoming[Operation[C]] = {
      def unexpected(x: Any) = this.unexpected(x, classOf[EventAppeared])

      in match {
        case Success(_: EventAppeared) => Continue(this, in)
        case Success(Unsubscribed)     => Stop(in)
        case Success(x)                => unexpected(x)
        case Failure(AccessDenied)     => accessDenied(s"Subscription on $stream dropped due to access denied")
        case Failure(x)                => unexpected(x)
      }
    }

    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectOut = {
      case Unsubscribe =>
        val pack = this.pack.copy(message = Unsubscribe)
        val operation = Unsubscribing(stream, pack, client, ongoing, version + 1)
        OnOutgoing.Continue(operation, pack)
      case ack: Ack =>
        val pack = this.pack.copy(message = ack)
        val operation = this
        OnOutgoing.Continue(operation, pack)
      case nak: Nak =>
        val pack = this.pack.copy(message = nak)
        val operation = this
        OnOutgoing.Continue(operation, pack)
    }

    def disconnected = {
      val operation = Connecting(connect, pack, client, ongoing = false, version + 1)
      OnDisconnected.Continue(operation)
    }

    def connected = {
      val operation = Connecting(connect, pack, client, ongoing = true, version + 1)
      OnConnected.Retry(operation, pack)
    }
  }

  final case class Unsubscribing[C](
      stream:  EventStream,
      pack:    PackOut,
      client:  C,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation[C] {

    def inspectIn(in: Try[In]) = {
      def unexpected(x: Any) = this.unexpected(x, Unsubscribed.getClass)

      in match {
        case Success(Unsubscribed)         => Stop(Unsubscribed)
        case Success(_: EventAppeared)     => Ignore
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

    def connected = OnConnected.Stop(Try(Unsubscribed))
  }
}