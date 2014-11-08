package eventstore
package tcp

import akka.actor.{ Status, ActorRef }
import scala.util.{ Failure, Try, Success }

sealed trait Operation {
  def id: Uuid

  def client: ActorRef

  def clientTerminated: Option[TcpPackageOut]

  def pack: TcpPackageOut

  def inspectOut: PartialFunction[Out, (Operation, Option[TcpPackageOut])] // TODO iterable and pass credentials

  def inspectIn(x: Try[In])(implicit actor: ActorRef): Option[(Operation, Option[TcpPackageOut])]

  def connectionLost: Option[Operation]

  def sendToClient(x: Try[In])(implicit actor: ActorRef) = {
    val reply = x match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    client ! reply
  }
}

object Operation {
  def apply(pack: TcpPackageOut, client: ActorRef): Operation = {
    pack.message match {
      case x: SubscribeTo => SubscriptionOperation(pack.correlationId, x, pack.credentials, client)
      case _              => SimpleOperation(pack, client)
    }
  }
}

case class SimpleOperation(pack: TcpPackageOut, client: ActorRef) extends Operation {
  def id = pack.correlationId

  def inspectIn(x: Try[In])(implicit actor: ActorRef) = {
    sendToClient(x)
    None
  }

  def clientTerminated = None

  def inspectOut = PartialFunction.empty

  def connectionLost = Some(this)
}

sealed trait SubscriptionOperation extends Operation

object SubscriptionOperation {
  def apply(id: Uuid, message: SubscribeTo, credentials: Option[UserCredentials], client: ActorRef): SubscriptionOperation = {
    new Subscribing(id, message, credentials, client)
  }

  case class Subscribing(
      id: Uuid,
      out: SubscribeTo,
      credentials: Option[UserCredentials],
      client: ActorRef, unsubscribe: Boolean = false) extends SubscriptionOperation {

    def clientTerminated = Some(TcpPackageOut(Unsubscribe, id, credentials))

    def inspectIn(x: Try[In])(implicit actor: ActorRef) = {
      val result = x match {
        case Failure(_) => None
        case Success(_: SubscribeCompleted) =>
          val result =
            if (unsubscribe) (Unsubscribing(id, credentials, client), Some(TcpPackageOut(Unsubscribe, id, credentials)))
            else (Subscribed(id, out, credentials, client), None)
          Some(result)

        case _ => Some(this -> None)
      }

      sendToClient(x)
      result
    }

    def inspectOut = {
      case Unsubscribe => (copy(unsubscribe = true), None)
    }

    def pack = TcpPackageOut(out, id, credentials)

    def connectionLost = Some(this)
  }

  case class Subscribed(id: Uuid, message: SubscribeTo, credentials: Option[UserCredentials], client: ActorRef) extends SubscriptionOperation {
    def inspectIn(x: Try[In])(implicit actor: ActorRef) = {
      x match {
        case Success(_: StreamEventAppeared) =>
          sendToClient(x)
          Some(this -> None)

        case Failure(_) =>
          sendToClient(x)
          Some(Unsubscribing(id, credentials, client) -> None)

        case _ =>
          None
      }
    }

    def clientTerminated = Some(TcpPackageOut(Unsubscribe, id, credentials))

    def inspectOut = {
      case Unsubscribe =>
        val out = Unsubscribing(id, credentials, client)
        val pack = TcpPackageOut(Unsubscribe, id, credentials)
        (out -> Some(pack))
    }

    def pack = sys.error("'pack' method is not supported")

    def connectionLost = Some(Subscribing(id, message, credentials, client))
  }

  case class Unsubscribing(id: Uuid, credentials: Option[UserCredentials], client: ActorRef) extends SubscriptionOperation {
    def inspectIn(x: Try[In])(implicit actor: ActorRef) = x match {
      case Success(UnsubscribeCompleted) =>
        sendToClient(x)
        None

      case Failure(_) =>
        sendToClient(x)
        Some(this -> None)

      case _ => Some(this -> None)
    }

    def clientTerminated = None
    def inspectOut = PartialFunction.empty
    def pack = TcpPackageOut(Unsubscribe, id, credentials)
    def connectionLost = None
  }
}