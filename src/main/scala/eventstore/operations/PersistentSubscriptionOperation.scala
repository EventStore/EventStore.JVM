package eventstore.operations

import akka.actor.ActorRef
import eventstore.In
import eventstore.PersistentSubscription._
import eventstore.operations.OnIncoming._
import eventstore.tcp.PackOut

import scala.util.Try

private[eventstore] class PersistentSubscriptionOperation(
  connect: Connect,
  pack: PackOut,
  val client: ActorRef,
  ongoing: Boolean) extends Operation {

  def id = pack.correlationId

  def connected = OnConnected.Retry(this, pack)

  def disconnected = OnDisconnected.Continue(this)

  def clientTerminated = None

  def inspectOut = PartialFunction.empty

  def inspectIn(in: Try[In]) = {
    println(in)
    Stop(in)
  }

  def version = 0
}

private[eventstore] object PersistentSubscriptionOperation {
  def apply(
    connect: Connect,
    pack: PackOut,
    client: ActorRef,
    ongoing: Boolean): Operation = new PersistentSubscriptionOperation(connect, pack, client, ongoing)
}
