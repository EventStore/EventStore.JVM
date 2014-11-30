package eventstore
package operations

import akka.actor.ActorRef
import NotHandled.{ TooBusy, NotReady }
import tcp.PackOut
import SubscriptionDropped.AccessDenied
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

  protected def stop(x: EsException): Option[SubscriptionOperation] = stop(Failure(x))

  protected def unexpected(actual: Any, expectedClass: Class[_]): Option[SubscriptionOperation] = {
    val expected = expectedClass.getSimpleName
    val msg = s"Expected: $expected, actual: $actual"
    stop(new CommandNotExpectedException(msg))
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
    outFunc: Option[OutFunc]): Operation = {
    new Subscribing(subscribeTo, pack, client, inFunc, outFunc, 0)
  }

  case class Subscribing(
      subscribeTo: SubscribeTo,
      pack: PackOut,
      client: ActorRef,
      inFunc: InFunc,
      outFunc: Option[OutFunc],
      version: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def clientTerminated() = {
      outFunc.foreach { outFunc => outFunc(pack.copy(message = Unsubscribe)) }
    }

    def inspectIn(in: Try[In]): Option[SubscriptionOperation] = {
      def retry() = {
        outFunc.foreach { outFunc => outFunc(pack) }
        Some(this)
      }

      def subscribed = outFunc match {
        case None => Some(this)
        case Some(outFunc) =>
          inFunc(in)
          Some(Subscribed(subscribeTo, pack, client, inFunc, outFunc, version + 1))
      }

      def eventAppeared = {
        inFunc(in)
        Some(this)
      }

      def accessDenied = {
        val msg = s"Subscription to $stream failed due to access denied"
        stop(new AccessDeniedException(msg))
      }

      def unexpected(x: Any) = {
        this.unexpected(x, Completed.expected)
      }

      in match {
        case Success(Completed())                      => subscribed
        case Success(EventAppeared())                  => eventAppeared
        case Success(x)                                => unexpected(x)
        case Failure(SubscriptionDropped.AccessDenied) => accessDenied
        case Failure(OperationTimedOut)                => stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))             => retry()
        case Failure(NotHandled(TooBusy))              => retry()
        case Failure(BadRequest)                       => stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(NotAuthenticated)                 => stop(NotAuthenticatedException(pack))
        case Failure(x)                                => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe =>
        outFunc.foreach { outFunc => outFunc(pack.copy(message = Unsubscribe)) }
        inFunc(Try(Unsubscribed))
        None
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
      version: Int) extends SubscriptionOperation {

    def stream = subscribeTo.stream

    def inspectIn(in: Try[In]) = {
      def unsubscribed = {
        inFunc(in)
        None
      }

      def eventAppeared = {
        inFunc(in)
        Some(this)
      }

      def accessDenied = {
        val msg = s"Subscription on $stream dropped due to access denied"
        stop(new AccessDeniedException(msg))
      }

      def unexpected(x: Any) = {
        this.unexpected(x, classOf[StreamEventAppeared])
      }

      in match {
        case Success(EventAppeared()) => eventAppeared
        case Success(Unsubscribed)    => unsubscribed
        case Success(x)               => unexpected(x)
        case Failure(AccessDenied)    => accessDenied
        case Failure(x)               => unexpected(x)
      }
    }

    def clientTerminated() = {
      outFunc(pack.copy(message = Unsubscribe))
    }

    def inspectOut = {
      case Unsubscribe =>
        val pack = this.pack.copy(message = Unsubscribe)
        outFunc(pack)
        val operation = Unsubscribing(stream, pack, client, inFunc, outFunc, version + 1)
        Some(operation)
    }

    def connectionLost() = {
      Some(Subscribing(subscribeTo, pack, client, inFunc, None, version + 1))
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
      def retry() = {
        outFunc(pack)
        Some(this)
      }

      def unexpected(x: Any) = {
        this.unexpected(x, Unsubscribed.getClass)
      }

      def accessDenied = {
        val msg = s"Unsubscribed from $stream due to access denied"
        stop(new AccessDeniedException(msg))
      }

      in match {
        case Success(Unsubscribed)                     => stop(Success(Unsubscribed))
        case Success(EventAppeared())                  => Some(this)
        case Success(x)                                => unexpected(x)
        case Failure(SubscriptionDropped.AccessDenied) => accessDenied
        case Failure(OperationTimedOut)                => stop(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))             => retry()
        case Failure(NotHandled(TooBusy))              => retry()
        case Failure(BadRequest)                       => stop(new ServerErrorException(s"Bad request: $pack"))
        case Failure(x)                                => unexpected(x)
      }
    }

    def clientTerminated() = {
      inFunc(Success(Unsubscribed))
    }

    def inspectOut = PartialFunction.empty

    def connectionLost() = stop(Success(Unsubscribed))

    def connected(outFunc: OutFunc) = stop(Success(Unsubscribed))
  }
}