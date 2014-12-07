package eventstore
package operations

import akka.actor.ActorRef
import tcp.PackOut
import scala.util.Try

private[eventstore] trait Operation {
  def id: Uuid

  def client: ActorRef

  def inspectOut: PartialFunction[Out, Option[Operation]] // TODO iterable and pass credentials

  def inspectIn(in: Try[In]): Option[Operation]

  // TODO prevent this from calling when disconnected
  def connectionLost(): Option[Operation]

  // TODO prevent this from calling when connected
  def connected(outFunc: OutFunc): Option[Operation]

  def clientTerminated(): Unit

  def version: Int
}

private[eventstore] object Operation {
  def opt(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]): Option[Operation] = {

    def base(x: Inspection) = Some(BaseOperation(pack, client, inFunc, outFunc, x))

    pack.message match {
      case x: WriteEvents       => base(new WriteEventsInspection(x))
      case x: DeleteStream      => base(new DeleteStreamInspection(x))
      case x: TransactionStart  => base(new TransactionStartInspection(x))
      case x: TransactionWrite  => base(new TransactionWriteInspection(x))
      case x: TransactionCommit => base(new TransactionCommitInspection(x))
      case x: ReadEvent         => base(new ReadEventInspection(x))
      case x: ReadStreamEvents  => base(new ReadStreamEventsInspection(x))
      case x: ReadAllEvents     => base(new ReadAllEventsInspection(x))
      case x: SubscribeTo       => Some(SubscriptionOperation(x, pack, client, inFunc, outFunc))
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