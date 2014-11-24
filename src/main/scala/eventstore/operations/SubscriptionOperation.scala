package eventstore
package operations

import akka.actor.ActorRef
import eventstore.NotHandled.{ TooBusy, NotReady }
import eventstore.tcp.PackOut
import scala.util.{ Success, Failure, Try }

sealed trait SubscriptionOperation extends Operation

// TODO track the difference of subscribe to stream vs to all
object SubscriptionOperation {

  def apply(
    id: Uuid,
    message: SubscribeTo,
    credentials: Option[UserCredentials],
    client: ActorRef,
    inFunc: InFunc,
    outFunc: Option[OutFunc]): Operation = {
    new Subscribing(id, message, credentials, client, inFunc, outFunc, 0)
  }

  case class Subscribing(
      id: Uuid,
      out: SubscribeTo,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: Option[OutFunc],
      version: Int) extends SubscriptionOperation {

    lazy val pack = PackOut(out, id, credentials)

    def clientTerminated() = {
      outFunc.foreach { outFunc => outFunc(PackOut(Unsubscribe, id, credentials)) }
    }

    def inspectIn(in: Try[In]) = {
      def failed(x: Throwable) = {
        inFunc(Failure(x))
        None
      }

      def retry() = {
        outFunc.foreach { outFunc => outFunc(pack) }
        Some(this)
      }

      def unexpected(x: Any) = failed(new CommandNotExpectedException(x.toString))

      def subscribed() = {
        outFunc match {
          case None => Some(this)
          case Some(outFunc) =>
            inFunc(in)
            Some(Subscribed(id, out, credentials, client, inFunc, outFunc, version + 1))
        }
      }

      def eventAppeared() = {
        inFunc(in)
        Some(this)
      }

      val streamId = out.stream

      in match {
        case Success(_: SubscribeCompleted)            => subscribed()
        case Success(_: StreamEventAppeared)           => eventAppeared()
        case Success(x)                                => unexpected(x)
        case Failure(SubscriptionDropped.AccessDenied) => failed(new AccessDeniedException(s"Subscription to $streamId failed due to access denied"))
        case Failure(OperationTimedOut)                => failed(OperationTimeoutException(pack))
        case Failure(NotHandled(NotReady))             => retry()
        case Failure(NotHandled(TooBusy))              => retry()
        case Failure(BadRequest)                       => failed(new ServerErrorException(s"Bad request: $pack"))
        case Failure(NotAuthenticated)                 => failed(NotAuthenticatedException(pack))
        case Failure(x)                                => unexpected(x)
      }
    }

    def inspectOut = {
      case Unsubscribe =>
        inFunc(Try(Unsubscribed))
        None
    }

    def connectionLost() = {
      val operation =
        if (outFunc.isEmpty) this
        else copy(outFunc = None)
      Some(operation)
    }

    def connected(outFunc: OutFunc) = {
      outFunc(pack)
      Some(copy(outFunc = Some(outFunc)))
    }
  }

  case class Subscribed(
      id: Uuid,
      message: SubscribeTo,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int) extends SubscriptionOperation {

    def inspectIn(in: Try[In]) = {
      in match {
        case Success(Unsubscribed) =>
          inFunc(in)
          None

        case Success(_: StreamEventAppeared) =>
          inFunc(in)
          Some(this)

        case Success(_)                 => Some(this)

        case Failure(OperationTimedOut) => Some(this)

        case Failure(_) => // TODO check this
          inFunc(in)
          Some(Unsubscribing(id, credentials, client, inFunc, outFunc, version + 1))
      }
    }

    def clientTerminated() = {
      outFunc(PackOut(Unsubscribe, id, credentials))
    }

    def inspectOut = {
      case Unsubscribe =>
        val operation = Unsubscribing(id, credentials, client, inFunc, outFunc, version + 1)
        outFunc(operation.pack)
        Some(operation)
    }

    def connectionLost() = {
      Some(Subscribing(id, message, credentials, client, inFunc, None, version + 1))
    }

    def connected(outFunc: OutFunc) = sys.error("should not be called")
  }

  case class Unsubscribing(
      id: Uuid,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int) extends SubscriptionOperation {

    lazy val pack = PackOut(Unsubscribe, id, credentials)

    def inspectIn(in: Try[In]) = {
      in match {
        case Success(Unsubscribed) =>
          inFunc(in)
          None

        case Success(_: StreamEventAppeared) => Some(this)

        case Success(_)                      => Some(this)

        case Failure(OperationTimedOut) =>
          inFunc(Failure(OperationTimeoutException(pack)))
          None

        case Failure(_) =>
          inFunc(in)
          Some(this)
      }
    }

    def clientTerminated = {}

    def inspectOut = PartialFunction.empty

    def connectionLost() = {
      inFunc(Success(Unsubscribed))
      None
    }

    def connected(outFunc: OutFunc) = sys.error("should not be called")
  }
}