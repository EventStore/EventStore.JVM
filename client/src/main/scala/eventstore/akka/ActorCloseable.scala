package eventstore
package akka

import java.io.Closeable
import _root_.akka.actor.{PoisonPill, ActorRef}

private[eventstore] final case class ActorCloseable(actor: ActorRef) extends Closeable {
  def close() = actor ! PoisonPill
}