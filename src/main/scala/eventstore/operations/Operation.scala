package eventstore
package operations

import akka.actor.ActorRef
import eventstore.tcp.PackOut
import scala.util.Try

trait Operation {
  def id: Uuid

  def client: ActorRef

  def clientTerminated(): Unit

  def inspectOut: PartialFunction[Out, Option[Operation]] // TODO iterable and pass credentials

  // TODO what if not handled, should I forward to client ?
  def inspectIn(in: Try[In]): Option[Operation]

  // TODO prevent this from calling when already disconnected
  def connectionLost(): Option[Operation]

  // TODO prevent this from calling when already connected
  def connected(outFunc: OutFunc): Option[Operation]

  def timedOut(): Option[Operation]

  def version: Int
}

object Operation {
  def apply(pack: PackOut, client: ActorRef, inFunc: InFunc, outFunc: Option[OutFunc]): Operation = {
    pack.message match {
      case x: WriteEvents      => WriteEventsOperation(pack, client, inFunc, outFunc)
      case x: DeleteStream     => DeleteStreamOperation(pack, client, inFunc, outFunc)
      case x: TransactionStart => TransactionStartOperation(pack, client, inFunc, outFunc)
      case x: TransactionWrite => TransactionWriteOperation(pack, client, inFunc, outFunc)
      case x: ReadEvent        => ReadEventOperation(pack, client, inFunc, outFunc)
      case x: SubscribeTo      => SubscriptionOperation(pack.correlationId, x, pack.credentials, client, inFunc, outFunc)
      case _                   => OutInOperation(pack, client, inFunc, outFunc)
    }
  }
}