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
  def connectionLost(): Option[Operation]

  // TODO prevent this from calling when connected
  def connected(outFunc: OutFunc): Option[Operation]

  def clientTerminated(): Unit

  def version: Int
}

private[eventstore] object Operation {
  def opt(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc], maxRetries: Int): Option[Operation] = {

    def base(x: Inspection) = Some(BaseOperation(pack, client, outFunc, x, maxRetries))

    pack.message match {
      case x: WriteEvents       => base(WriteEventsInspection(x))
      case x: DeleteStream      => base(DeleteStreamInspection(x))
      case x: TransactionStart  => base(TransactionStartInspection(x))
      case x: TransactionWrite  => base(TransactionWriteInspection(x))
      case x: TransactionCommit => base(TransactionCommitInspection(x))
      case x: ReadEvent         => base(ReadEventInspection(x))
      case x: ReadStreamEvents  => base(ReadStreamEventsInspection(x))
      case x: ReadAllEvents     => base(ReadAllEventsInspection(x))
      case x: SubscribeTo       => Some(SubscriptionOperation(x, pack, client, inFunc, outFunc, maxRetries))
      case Unsubscribe          => base(UnsubscribeInspection)
      case ScavengeDatabase     => base(ScavengeDatabaseInspection)
      case Authenticate         => base(AuthenticateInspection)
      case Ping                 => base(PingInspection)
      case Pong                 => None
      case HeartbeatRequest     => base(HeartbeatRequestInspection)
      case HeartbeatResponse    => None
    }
  }
}