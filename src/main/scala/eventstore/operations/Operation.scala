package eventstore
package operations

import eventstore.tcp.PackOut
import eventstore.{ PersistentSubscription => Ps }

import scala.util.{ Failure, Try }

private[eventstore] trait Operation[C] {
  type Self = Operation[C]

  def id: Uuid
  def client: C
  def inspectOut: PartialFunction[Out, OnOutgoing[Self]]
  def inspectIn(in: Try[In]): OnIncoming[Self]
  def disconnected: OnDisconnected[Self]
  def connected: OnConnected[Self]
  def clientTerminated: Option[PackOut]
  def version: Int

}

private[eventstore] object Operation {
  def opt[C](pack: PackOut, client: C, connected: Boolean, maxRetries: Int): Option[Operation[C]] = {
    def retryable(x: Operation[C]) = RetryableOperation(x, maxRetries, connected)
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
      case _: IdentifyClient    => Some(simple(IdentifyClientInspection))
      case Ping                 => Some(simple(PingInspection))
      case Pong                 => None
      case HeartbeatRequest     => Some(simple(HeartbeatRequestInspection))
      case HeartbeatResponse    => None
      case x: Ps.Connect        => Some(retryable(PersistentSubscriptionOperation(x, pack, client, connected)))
      case x: Ps.Create         => Some(simple(CreatePersistentSubscriptionInspection(x)))
      case x: Ps.Update         => Some(simple(UpdatePersistentSubscriptionInspection(x)))
      case x: Ps.Delete         => Some(simple(DeletePersistentSubscriptionInspection(x)))
      case _: Ps.Ack            => None
      case _: Ps.Nak            => None
    }
  }
}

sealed trait OnConnected[+A]

object OnConnected {
  case class Retry[A](a: A, out: PackOut) extends OnConnected[A]
  case class Stop(in: Try[In]) extends OnConnected[Nothing]
}

sealed trait OnIncoming[+A]

object OnIncoming {
  case class Stop(in: Try[In]) extends OnIncoming[Nothing]

  object Stop {
    def apply(x: EsException): Stop = Stop(Failure(x))
    def apply(x: In): Stop = Stop(Try(x))
  }

  case class Retry[A](a: A, pack: PackOut) extends OnIncoming[A]
  case class Continue[A](a: A, in: Try[In]) extends OnIncoming[A]
  case object Ignore extends OnIncoming[Nothing]
}

sealed trait OnOutgoing[+A]

object OnOutgoing {
  case class Stop(out: PackOut, in: Try[In]) extends OnOutgoing[Nothing]
  case class Continue[A](a: A, out: PackOut) extends OnOutgoing[A]
}

sealed trait OnDisconnected[+A]

object OnDisconnected {
  case class Continue[A](a: A) extends OnDisconnected[A]
  case class Stop(in: Try[In]) extends OnDisconnected[Nothing]
}