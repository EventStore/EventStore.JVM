package eventstore
package operations

import akka.actor.ActorRef
import eventstore.tcp.TcpPackageOut
import scala.util.{ Success, Failure, Try }

sealed trait SubscriptionOperation extends Operation

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

    def clientTerminated() = {
      outFunc.foreach { outFunc => outFunc(TcpPackageOut(Unsubscribe, id, credentials)) }
    }

    def inspectIn(in: Try[In]) = {
      in match {
        case Success(_: SubscribeCompleted) =>
          outFunc match {
            case None => Some(this)
            case Some(outFunc) =>
              inFunc(in)
              Some(Subscribed(id, out, credentials, client, inFunc, outFunc, version + 1))
          }

        case Success(_: StreamEventAppeared) =>
          inFunc(in)
          Some(this)

        case Success(_) => Some(this)

        case Failure(_) =>
          inFunc(in)
          None
      }
    }

    def inspectOut = {
      case Unsubscribe =>
        inFunc(Failure(EsException(EsError.Error, Some("Not yet subscribed"))))
        Some(this)
    }

    def connectionLost() = {
      val operation =
        if (outFunc.isEmpty) this
        else copy(outFunc = None)
      Some(operation)
    }

    def connected(outFunc: OutFunc) = {
      outFunc(TcpPackageOut(out, id, credentials))
      Some(copy(outFunc = Some(outFunc)))
    }

    def timedOut() = {
      inspectIn(Failure(EsException(EsError.OperationTimedOut(out))))
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
        case Success(UnsubscribeCompleted) =>
          inFunc(in)
          None

        case Success(_: StreamEventAppeared) =>
          inFunc(in)
          Some(this)

        case Success(_) => Some(this)

        case Failure(EsException(EsError.OperationTimedOut(_), _)) =>
          Some(this)

        case Failure(_) =>
          inFunc(in)
          Some(Unsubscribing(id, credentials, client, inFunc, outFunc, version + 1))
      }
    }

    def clientTerminated() = {
      outFunc(TcpPackageOut(Unsubscribe, id, credentials))
    }

    def inspectOut = {
      case Unsubscribe =>
        println("Unsubscribe")
        outFunc(TcpPackageOut(Unsubscribe, id, credentials))
        Some(Unsubscribing(id, credentials, client, inFunc, outFunc, version + 1))
    }

    def connectionLost() = {
      Some(Subscribing(id, message, credentials, client, inFunc, None, version + 1))
    }

    def connected(outFunc: OutFunc) = sys.error("should not be called")

    def timedOut() = Some(this)
  }

  case class Unsubscribing(
      id: Uuid,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc,
      version: Int) extends SubscriptionOperation {

    def inspectIn(in: Try[In]) = in match {
      case Success(UnsubscribeCompleted) =>
        inFunc(in)
        None

      case Success(_: StreamEventAppeared) => Some(this)

      case Success(_)                      => Some(this)

      case Failure(EsException(EsError.OperationTimedOut(_), _)) =>
        inFunc(in)
        None

      case Failure(_) =>
        inFunc(in)
        Some(this)
    }

    def clientTerminated = {}

    def inspectOut = PartialFunction.empty

    def connectionLost() = {
      inFunc(Success(UnsubscribeCompleted))
      None
    }

    def connected(outFunc: OutFunc) = sys.error("should not be called")

    def timedOut = {
      inspectIn(Failure(EsException(EsError.OperationTimedOut(Unsubscribe))))
    }
  }
}