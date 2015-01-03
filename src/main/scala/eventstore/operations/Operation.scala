package eventstore
package operations

import akka.actor.ActorRef
import eventstore.tcp.PackOut
import scala.util.{ Failure, Try }

private[eventstore] trait Operation {
  def id: Uuid

  def client: ActorRef

  def inspectOut: PartialFunction[Out, OnOutgoing]

  def inspectIn(in: Try[In]): OnIncoming

  def disconnected: OnDisconnected

  def connected: OnConnected

  def clientTerminated: Option[PackOut]

  def version: Int
}

private[eventstore] object Operation {
  def opt(pack: PackOut, client: ActorRef, connected: Boolean, maxRetries: Int): Option[Operation] = {
    def retryable(x: Operation) = RetryableOperation(x, maxRetries, connected)
    def base(x: Inspection) = retryable(BaseOperation(pack, client, x))

    pack.message match {
      case x: WriteEvents       => Some(base(WriteEventsInspection(x)))
      case x: DeleteStream      => Some(base(DeleteStreamInspection(x)))
      case x: TransactionStart  => Some(base(TransactionStartInspection(x)))
      case x: TransactionWrite  => Some(base(TransactionWriteInspection(x)))
      case x: TransactionCommit => Some(base(TransactionCommitInspection(x)))
      case x: ReadEvent         => Some(base(ReadEventInspection(x)))
      case x: ReadStreamEvents  => Some(base(ReadStreamEventsInspection(x)))
      case x: ReadAllEvents     => Some(base(ReadAllEventsInspection(x)))
      case x: SubscribeTo       => Some(retryable(SubscriptionOperation(x, pack, client, connected)))
      case Unsubscribe          => Some(base(UnsubscribeInspection))
      case ScavengeDatabase     => Some(base(ScavengeDatabaseInspection))
      case Authenticate         => Some(base(AuthenticateInspection))
      case Ping                 => Some(base(PingInspection))
      case Pong                 => None
      case HeartbeatRequest     => Some(base(HeartbeatRequestInspection))
      case HeartbeatResponse    => None
    }
  }
}

sealed trait OnConnected

object OnConnected {
  case class Retry(operation: Operation, out: PackOut) extends OnConnected
  case class Stop(in: Try[In]) extends OnConnected
}

sealed trait OnIncoming

object OnIncoming {
  case class Stop(in: Try[In]) extends OnIncoming

  object Stop {
    def apply(x: EsException): Stop = Stop(Failure(x))
    def apply(x: In): Stop = Stop(Try(x))
  }

  case class Retry(operation: Operation, pack: PackOut) extends OnIncoming
  case class Continue(operation: Operation, in: Try[In]) extends OnIncoming
  case object Ignore extends OnIncoming
}

sealed trait OnOutgoing

object OnOutgoing {
  case class Stop(out: PackOut, in: Try[In]) extends OnOutgoing
  case class Continue(operation: Operation, out: PackOut) extends OnOutgoing
}

sealed trait OnDisconnected

object OnDisconnected {
  case class Continue(operation: Operation) extends OnDisconnected
  case class Stop(in: Try[In]) extends OnDisconnected
}