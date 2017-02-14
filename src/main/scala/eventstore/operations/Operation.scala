package eventstore
package operations

import eventstore.tcp.{ Client, PackOut }
import eventstore.{ PersistentSubscription => Ps }

import scala.util.{ Failure, Try }

private[eventstore] trait Operation {
  def id: Uuid

  def client: Client

  def inspectOut: PartialFunction[Out, OnOutgoing]

  def inspectIn(in: Try[In]): OnIncoming

  def disconnected: OnDisconnected

  def connected: OnConnected

  def clientTerminated: Option[PackOut]

  def version: Int
}

private[eventstore] object Operation {
  def opt(pack: PackOut, client: Client, connected: Boolean, maxRetries: Int): Option[Operation] = {
    def retryable(x: Operation) = RetryableOperation(x, maxRetries, connected)
    def simple(x: Inspection) = retryable(SimpleOperation(pack, client, x))

    pack.message match {
      case x: WriteEvents       => Some(simple(WriteEventsInspection(x)))
      case x: DeleteStream      => Some(simple(DeleteStreamInspection(x)))
      case x: TransactionStart  => Some(simple(TransactionStartInspection(x)))
      case x: TransactionWrite  => Some(simple(TransactionWriteInspection(x)))
      case x: TransactionCommit => Some(simple(TransactionCommitInspection(x)))
      case x: ReadEvent         => Some(simple(ReadEventInspection(x)))
      case x: ReadStreamEvents  => Some(simple(ReadStreamEventsInspection(x)))
      case x: ReadAllEvents     => Some(simple(ReadAllEventsInspection(x)))
      case x: SubscribeTo       => Some(retryable(SubscriptionOperation(x, pack, client, connected)))
      case Unsubscribe          => Some(simple(UnsubscribeInspection))
      case ScavengeDatabase     => Some(simple(ScavengeDatabaseInspection))
      case Authenticate         => Some(simple(AuthenticateInspection))
      case Ping                 => Some(simple(PingInspection))
      case Pong                 => None
      case HeartbeatRequest     => Some(simple(HeartbeatRequestInspection))
      case HeartbeatResponse    => None
      case x: Ps.Connect        => Some(retryable(PersistentSubscriptionOperation(x, pack, client, connected)))
      case x: Ps.Create         => Some(simple(CreatePersistentSubscriptionInspection(x)))
      case x: Ps.Update         => Some(simple(UpdatePersistentSubscriptionInspection(x)))
      case x: Ps.Delete         => Some(simple(DeletePersistentSubscriptionInspection(x)))
      case x: Ps.Ack            => None
      case x: Ps.Nak            => None
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