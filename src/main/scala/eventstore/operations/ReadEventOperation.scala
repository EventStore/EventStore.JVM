package eventstore
package operations

import akka.actor.ActorRef
import eventstore.EsError.NotHandled.{ NotMaster, TooBusy, NotReady }
import eventstore.tcp.PackOut

import scala.util.{ Success, Failure, Try }

case class ReadEventOperation(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]) extends Operation {
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

    def success() = {
      inFunc(in)
      None
    }

    in match {
      case Success(_: ReadEventCompleted)                  => success()

      case Success(x)                                      => unexpectedReply(x)

      case Failure(EsException(EsError.EventNotFound, _))  => success()

      case Failure(EsException(EsError.StreamNotFound, _)) => success()

      case Failure(EsException(EsError.StreamDeleted, _))  => success()

      case Failure(x @ EsException(EsError.NotHandled(reason), _)) => reason match {
        case NotReady     => retry()
        case TooBusy      => retry()
        case NotMaster(_) => unexpectedReply(x)
      }

      case Failure(x @ EsException(EsError.AccessDenied, None)) =>
        val readEvent = pack.message.asInstanceOf[ReadEvent]
        val exception = x.copy(message = Some(s"Read access denied for stream ${readEvent.streamId}"))
        inFunc(Failure(exception))
        None

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