package eventstore
package operations

import akka.actor.ActorRef
import eventstore.EsError.NotHandled.{ NotMaster, TooBusy, NotReady }
import eventstore.tcp.PackOut

import scala.util.{ Success, Failure, Try }

case class TransactionStartOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]) extends Operation {
  def id = pack.correlationId

  def inspectIn(in: Try[In]) = {

    def unexpectedReply(in: Any) = {
      val out = pack.message
      sys.error(s"Unexpected reply for $out: $in")
    }

    def retry() = {
      outFunc.foreach { outFunc => outFunc(pack) }
      Some(this)
    }

    def succeed() = {
      inFunc(in)
      None
    }

    in match {
      case Success(_: TransactionStartCompleted)                       => succeed()

      case Failure(EsException(EsError.PrepareTimeout, _))             => retry()

      case Failure(EsException(EsError.CommitTimeout, _))              => retry()

      case Failure(EsException(EsError.ForwardTimeout, _))             => retry()

      case Failure(EsException(EsError.WrongExpectedVersion, Some(_))) => succeed()

      case Failure(x @ EsException(EsError.WrongExpectedVersion, None)) =>
        val transactionStart = pack.message.asInstanceOf[TransactionStart]
        val message = s"Transaction start failed due to WrongExpectedVersion: ${transactionStart.streamId}, ${transactionStart.expectedVersion}"
        val exception = x.copy(message = Some(message))
        inFunc(Failure(exception))
        None

      case Failure(EsException(EsError.StreamDeleted, _))      => succeed()

      case Failure(EsException(EsError.InvalidTransaction, _)) => succeed()

      case Failure(x @ EsException(EsError.NotHandled(reason), _)) => reason match {
        case NotReady     => retry()
        case TooBusy      => retry()
        case NotMaster(_) => unexpectedReply(x)
      }

      case Failure(EsException(EsError.AccessDenied, Some(_))) => succeed()

      case Failure(x @ EsException(EsError.AccessDenied, None)) =>
        val writeEvents = pack.message.asInstanceOf[WriteEvents]
        val exception = x.copy(message = Some(s"Write access denied for ${writeEvents.streamId}"))
        inFunc(Failure(exception))
        None

      case Success(x) => unexpectedReply(x)

      case Failure(x) => unexpectedReply(x)
    }
  }

  def clientTerminated() = {}

  def inspectOut = PartialFunction.empty

  def connectionLost() = Some(this)

  def connected(outFunc: OutFunc) = {
    outFunc(pack)
    Some(this)
  }

  def timedOut = {
    val timedOut = EsError.OperationTimedOut(pack.message)
    inspectIn(Failure(EsException(timedOut)))
  }

  def version = 0
}