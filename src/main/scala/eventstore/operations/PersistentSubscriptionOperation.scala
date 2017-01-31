package eventstore
package operations

import eventstore.NotHandled.{ NotReady, TooBusy }
import eventstore.PersistentSubscription.{ Ack, Connect, EventAppeared }
import eventstore.tcp.{ Client, PackOut }
import eventstore.SubscriptionDropped._
import eventstore.operations.OnIncoming._

import scala.util.{ Failure, Success, Try }

private[eventstore] sealed trait PersistentSubscriptionOperation extends Operation {
  def stream: EventStream

  def pack: PackOut

  def id = pack.correlationId

  protected def unexpected(actual: Any, expectedClass: Class[_]): OnIncoming = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    Stop(new CommandNotExpectedException(msg))
  }

  protected def retry: OnIncoming = Retry(this, pack)

  protected def accessDenied(msg: String): OnIncoming = Stop(new AccessDeniedException(msg))
}

private[eventstore] object PersistentSubscriptionOperation {

  def apply(
    connect: Connect,
    pack:    PackOut,
    client:  Client,
    ongoing: Boolean
  ): Operation = {
    Connecting(connect, pack, client, ongoing, 0)
  }

  case class Connecting(
      connect: Connect,
      pack:    PackOut,
      client:  Client,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation {

    def stream = connect.streamId

    def clientTerminated = Some(pack.copy(message = Unsubscribe))

    def inspectIn(in: Try[In]) = {
      def subscribed =
        if (!ongoing) Ignore
        else Continue(Connected(connect, pack, client, ongoing, version + 1), in)

      def unexpected(x: Any) = this.unexpected(x, classOf[Connected])

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

  case class Connected(
      connect: Connect,
      pack:    PackOut,
      client:  Client,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation {

    def stream = connect.streamId

    def inspectIn(in: Try[In]): OnIncoming = {
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

  case class Unsubscribing(
      stream:  EventStream,
      pack:    PackOut,
      client:  Client,
      ongoing: Boolean,
      version: Int
  ) extends PersistentSubscriptionOperation {

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