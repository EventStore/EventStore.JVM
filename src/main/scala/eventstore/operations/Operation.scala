package eventstore
package operations

import akka.actor.ActorRef
import tcp.PackOut
import scala.util.Try

private[eventstore] trait Operation {
  def id: Uuid

  def client: ActorRef

  def inspectOut: PartialFunction[Out, Option[Operation]] // TODO iterable and pass credentials

  def inspectIn(in: Try[In]): Decision

  // TODO prevent this from calling when disconnected
  def disconnected: OnDisconnected

  // TODO prevent this from calling when connected
  def connected(outFunc: OutFunc): Option[Operation]

  def clientTerminated: Option[PackOut]

  def version: Int
}

private[eventstore] object Operation {
  def opt(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc], maxRetries: Int): Option[Operation] = {
    def retryable(x: Operation) = RetryableOperation(x, maxRetries, outFunc.isDefined)
    def base(x: Inspection) = retryable(BaseOperation(pack, client, outFunc, x))

    pack.message match {
      case x: WriteEvents       => Some(base(WriteEventsInspection(x)))
      case x: DeleteStream      => Some(base(DeleteStreamInspection(x)))
      case x: TransactionStart  => Some(base(TransactionStartInspection(x)))
      case x: TransactionWrite  => Some(base(TransactionWriteInspection(x)))
      case x: TransactionCommit => Some(base(TransactionCommitInspection(x)))
      case x: ReadEvent         => Some(base(ReadEventInspection(x)))
      case x: ReadStreamEvents  => Some(base(ReadStreamEventsInspection(x)))
      case x: ReadAllEvents     => Some(base(ReadAllEventsInspection(x)))
      case x: SubscribeTo       => Some(retryable(SubscriptionOperation(x, pack, client, inFunc, outFunc)))
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