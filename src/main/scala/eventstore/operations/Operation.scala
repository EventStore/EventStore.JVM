package eventstore
package operations

import akka.actor.ActorRef
import tcp.PackOut
import scala.util.Try

private[eventstore] trait Operation {
  def id: Uuid

  def client: ActorRef

  def inspectOut: PartialFunction[Out, Option[Operation]] // TODO iterable and pass credentials

  // TODO what if not handled, should I forward to client ?
  def inspectIn(in: Try[In]): Option[Operation]

  // TODO prevent this from calling when already disconnected
  def connectionLost(): Option[Operation]

  // TODO prevent this from calling when already connected
  def connected(outFunc: OutFunc): Option[Operation]

  def clientTerminated(): Unit

  def version: Int
}

private[eventstore] object Operation {
  def apply(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]): Operation = {

    def base(x: Inspection) = BaseOperation(pack, client, inFunc, outFunc, x)

    pack.message match {
      case x: WriteEvents       => base(new WriteEventsInspection(x))
      case x: DeleteStream      => base(new DeleteStreamInspection(x))
      case x: TransactionStart  => base(new TransactionStartInspection(x))
      case x: TransactionWrite  => base(new TransactionWriteInspection(x))
      case x: TransactionCommit => base(new TransactionCommitInspection(x))
      case x: ReadEvent         => base(new ReadEventInspection(x))
      case x: ReadStreamEvents  => base(new ReadStreamEventsInspection(x))
      case x: ReadAllEvents     => base(new ReadAllEventsInspection(x))
      case x: SubscribeTo       => SubscriptionOperation(x, pack, client, inFunc, outFunc)
      case Unsubscribe          => base(UnsubscribeInspection)
      case ScavengeDatabase     => base(ScavengeDatabaseInspection)
      case Authenticate         => base(AuthenticateInspection)
      case Ping                 => base(PingInspection)
      case Pong                 => ???
      case HeartbeatRequest     => base(HeartbeatRequestInspection)
      case HeartbeatResponse    => ???
    }
  }
}