package eventstore
package akka
package tcp

import java.net.InetSocketAddress
import scala.util.{ Failure, Success, Try }
import _root_.akka.actor.{ ActorContext, ActorRef, ActorSystem, Status }
import eventstore.tcp.PackOut

private[eventstore] final case class Client(private val ref: ActorRef) {
  def apply(in: Try[In])(implicit sender: ActorRef): Unit = {
    val msg = in match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    ref ! msg
  }

  def apply(in: Status.Failure)(implicit sender: ActorRef): Unit = ref ! in

  def watch()(implicit context: ActorContext): Unit = { context watch ref; () }

  def unwatch()(implicit context: ActorContext): Unit = { context unwatch ref; () }
}

private[eventstore] final case class Connection private (address: InetSocketAddress, private val ref: ActorRef) {
  def apply(packOut: PackOut): Unit = ref ! packOut

  def stop()(implicit system: ActorSystem, context: ActorContext): Unit = {
    context unwatch ref
    system stop ref
  }

  def unwatch()(implicit context: ActorContext): Unit = { context unwatch ref; () }

  def unapply(ref: ActorRef): Boolean = ref == this.ref
}

private[eventstore] object Connection {
  def apply(address: InetSocketAddress, ref: ActorRef, context: ActorContext): Connection = {
    Connection(address, context watch ref)
  }
}