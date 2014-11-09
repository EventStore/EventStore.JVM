package eventstore
package tcp

import akka.actor.ActorRef
import scala.util.{ Failure, Try, Success }
import Operation.{ OutFunc, InFunc }

sealed trait Operation {
  def id: Uuid

  def client: ActorRef

  def clientTerminated: Option[TcpPackageOut] // TODO refactor

  def inspectOut: PartialFunction[Out, Option[Operation]] // TODO iterable and pass credentials

  def inspectIn(in: Try[In]): Option[Operation]

  // TODO connectionLost can be rewritten via inspectIn(EsError.ConnectionLost)
  def connectionLost(): Option[Operation]

  def connected(outFunc: OutFunc): Option[Operation]
}

object Operation {
  type OutFunc = TcpPackageOut => Unit
  type InFunc = Try[In] => Unit

  def apply(pack: TcpPackageOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]): Operation = {
    pack.message match {
      case x: SubscribeTo => SubscriptionOperation(pack.correlationId, x, pack.credentials, client, inFunc, outFunc)
      case _              => SimpleOperation(pack, client, inFunc)
    }
  }
}

case class SimpleOperation(pack: TcpPackageOut, client: ActorRef, inFunc: InFunc) extends Operation {
  def id = pack.correlationId

  def inspectIn(in: Try[In]) = {
    inFunc(in)
    None
  }

  def clientTerminated = None

  def inspectOut = PartialFunction.empty

  def connectionLost() = Some(this)

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(this)
  }
}

sealed trait SubscriptionOperation extends Operation

object SubscriptionOperation {
  def apply(id: Uuid,
            message: SubscribeTo,
            credentials: Option[UserCredentials],
            client: ActorRef,
            inFunc: InFunc,
            outFunc: Option[OutFunc]): SubscriptionOperation = {
    new Subscribing(id, message, credentials, client, false, inFunc, outFunc)
  }
  // TODO maybe new state resubscribe?

  case class Subscribing(
      id: Uuid,
      out: SubscribeTo,
      credentials: Option[UserCredentials],
      client: ActorRef,
      unsubscribe: Boolean = false,
      inFunc: InFunc,
      outFunc: Option[OutFunc]) extends SubscriptionOperation {

    def clientTerminated = Some(TcpPackageOut(Unsubscribe, id, credentials))

    def inspectIn(in: Try[In]) = {

      in match {
        case Failure(_) =>
          inFunc(in)
          if (unsubscribe) inFunc(Success(UnsubscribeCompleted))
          None

        case Success(_: SubscribeCompleted) =>

          if (unsubscribe) {
            inFunc(in)
            outFunc match {
              case None =>
                inFunc(Success(UnsubscribeCompleted))
                None

              case Some(outFunc) =>
                outFunc(TcpPackageOut(Unsubscribe, id, credentials))
                Some(Unsubscribing(id, credentials, client, inFunc, outFunc))
            }
          } else {
            outFunc match {
              case Some(outFunc) =>
                inFunc(in)
                Some(Subscribed(id, out, credentials, client, inFunc, outFunc))
              case None => Some(this)
            }
          }

        case _ =>
          // TODO not handled, should I forward to client or retry ?
          Some(this)
      }

    }

    // TODO why not go to Unsubscribe state?
    def inspectOut = {
      case Unsubscribe =>
        outFunc match {
          case Some(outFunc) =>
            Some(
              if (unsubscribe) this // TODO should not happen
              else copy(unsubscribe = true))

          case None =>
            inFunc(Success(UnsubscribeCompleted))
            None
        }
      //        (copy(unsubscribe = true), None)
    }

    //    def pack = TcpPackageOut(out, id, credentials)

    def connectionLost() = {
      Some(
        if (outFunc.isEmpty) this
        else copy(outFunc = None))
    }

    def connected(outFunc: OutFunc) = {
      outFunc(TcpPackageOut(out, id, credentials))
      Some(copy(outFunc = Some(outFunc)))
    }
  }

  case class Subscribed(
      id: Uuid,
      message: SubscribeTo,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc) extends SubscriptionOperation {

    def inspectIn(in: Try[In]) = {
      in match {
        case Success(UnsubscribeCompleted) =>
          inFunc(in)
          None

        case Success(_: StreamEventAppeared) =>
          inFunc(in)
          Some(this)

        case Failure(_) =>
          inFunc(in)
          Some(Unsubscribing(id, credentials, client, inFunc, outFunc))

        case _ =>
          // TODO not handled, should I forward to client or retry ?
          Some(this)
      }
    }

    def clientTerminated = Some(TcpPackageOut(Unsubscribe, id, credentials))

    def inspectOut = {
      case Unsubscribe =>
        outFunc(TcpPackageOut(Unsubscribe, id, credentials))
        Some(Unsubscribing(id, credentials, client, inFunc, outFunc))
    }

    //    def pack = sys.error("'pack' method is not supported")

    def connectionLost() = {
      // TODO maybe new state resubscribing?
      Some(Subscribing(id, message, credentials, client, false, inFunc, None))
    }

    def connected(pipeline: TcpPackageOut => Unit) = {
      // TODO actually this should never happen

      Some(this)
      //      pipeline(TcpPackageOut(message, id, credentials))
      //      Some(Subscribing(id, message, credentials, client))
    }
  }

  case class Unsubscribing(
      id: Uuid,
      credentials: Option[UserCredentials],
      client: ActorRef,
      inFunc: InFunc,
      outFunc: OutFunc) extends SubscriptionOperation {
    def inspectIn(in: Try[In]) = in match {
      //      case Success(_: SubscribeCompleted) =>
      //        inFunc(in)
      //        Some(this)

      case Success(UnsubscribeCompleted) =>
        inFunc(in)
        None

      case Failure(_) =>
        inFunc(in)
        Some(this)

      case _ =>
        // TODO not handled, should I forward to client or retry ?
        Some(this)
    }

    def clientTerminated = None

    def inspectOut = PartialFunction.empty

    def connectionLost() = {
      inFunc(Success(UnsubscribeCompleted))
      None
    }

    def connected(pipeline: (TcpPackageOut) => Unit) = {
      // TODO actually this should never happen
      None
    }
  }
}