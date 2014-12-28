package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import SubscriptionDropped.AccessDenied
import Decision._
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

  protected def unexpected(actual: Any, expectedClass: Class[_]): Decision = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    Stop(new CommandNotExpectedException(msg))
  }

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
    outFunc: Option[OutFunc],
    maxRetries: Int): Operation = {
    Subscribing(subscribeTo, pack, client, inFunc, outFunc, 0, maxRetries, maxRetries)
  }

  case class Subscribing(
      subscribeTo: SubscribeTo,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: Option[OutFunc],
      version: Int,
      retriesLeft: Int,
      maxRetries: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def clientTerminated() = {
      outFunc.foreach { outFunc => outFunc(pack.copy(message = Unsubscribe)) }
    }

    def inspectIn(in: Try[In]): Decision = {
      def retry() = {
        outFunc match {
          case None => Retry(this, pack)
          case Some(outFunc) =>
            if (retriesLeft > 0) {
              Retry(copy(retriesLeft = retriesLeft - 1), pack)
            } else {
              Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
            }
        }
      }

      def subscribed = outFunc match {
        case None => Ignore
        case Some(outFunc) =>
          Continue(Subscribed(subscribeTo, pack, client, inFunc, outFunc, version + 1, maxRetries), in)
      }

      def accessDenied = {
        val msg = s"Subscription to $stream failed due to access denied"
        Stop(new AccessDeniedException(msg))
      }

      def unexpected(x: Any) = this.unexpected(x, Completed.expected)

      in match {
        case Success(Completed())                      => subscribed
        case Success(EventAppeared())                  => Continue(this, in)
        case Success(x)                                => unexpected(x)
        case Failure(SubscriptionDropped.AccessDenied) => accessDenied
        case Failure(OperationTimedOut)                => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))             => retry()
        case Failure(NotHandled(TooBusy))              => retry()
        case Failure(BadRequest)                       => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(NotAuthenticated)                 => Stop(NotAuthenticatedException(pack))
        case Failure(x)                                => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe =>
        outFunc.foreach { outFunc => outFunc(pack.copy(message = Unsubscribe)) }
        stop(Try(Unsubscribed))
    }

    def connectionLost(): Option[Subscribing] = {
      val operation = outFunc.fold(this) { _ => copy(outFunc = None) }
      Some(operation)
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
      version: Int,
      maxRetries: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def inspectIn(in: Try[In]): Decision = {
      def accessDenied = {
        val msg = s"Subscription on $stream dropped due to access denied"
        Stop(new AccessDeniedException(msg))
      }

      def unexpected(x: Any) = this.unexpected(x, classOf[StreamEventAppeared])

      in match {
        case Success(EventAppeared()) => Continue(this, in)
        case Success(Unsubscribed)    => Stop(in)
        case Success(x)               => unexpected(x)
        case Failure(AccessDenied)    => accessDenied
        case Failure(x)               => unexpected(x)
      }
    }

    def clientTerminated() = outFunc(pack.copy(message = Unsubscribe))

    def inspectOut = {
      case Unsubscribe =>
        val pack = this.pack.copy(message = Unsubscribe)
        outFunc(pack)
        val operation = Unsubscribing(stream, pack, client, inFunc, outFunc, version + 1, maxRetries, maxRetries)
        Some(operation)
    }

    def connectionLost() = {
      Some(Subscribing(subscribeTo, pack, client, inFunc, None, version + 1, maxRetries, maxRetries))
    }

    def connected(outFunc: OutFunc) = {
      // TODO correlate maxRetries with outFunc(pack)
      outFunc(pack)
      Some(Subscribing(subscribeTo, pack, client, inFunc, Some(outFunc), version + 1, maxRetries - 1, maxRetries))
    }
  }

  case class Unsubscribing(
      stream: EventStream,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int,
      retriesLeft: Int,
      maxRetries: Int) extends SubscriptionOperation {

    def inspectIn(in: Try[In]) = {
      def retry() = {
        if (retriesLeft > 0) {
          Retry(copy(retriesLeft = retriesLeft - 1), pack)
        } else {
          Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
        }
      }

      def unexpected(x: Any) = this.unexpected(x, Unsubscribed.getClass)

      def accessDenied = {
        val msg = s"Unsubscribed from $stream due to access denied"
        Stop(new AccessDeniedException(msg))
      }

      in match {
        case Success(Unsubscribed)                     => Stop(Unsubscribed)
        case Success(EventAppeared())                  => Ignore
        case Success(x)                                => unexpected(x)
        case Failure(SubscriptionDropped.AccessDenied) => accessDenied
        case Failure(OperationTimedOut)                => Stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))             => retry()
        case Failure(NotHandled(TooBusy))              => retry()
        case Failure(BadRequest)                       => Stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(x)                                => unexpected(x)
      }
    }

    def clientTerminated() = inFunc(Success(Unsubscribed))

    def inspectOut = PartialFunction.empty

    def connectionLost() = stop(Success(Unsubscribed))

    def connected(outFunc: OutFunc) = stop(Success(Unsubscribed))
  }
}