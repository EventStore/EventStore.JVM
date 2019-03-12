package eventstore
package akka

import java.io.Closeable
import _root_.akka.actor.{PoisonPill, ActorRef}

case class ActorCloseable(actor: ActorRef) extends Closeable {
  def close() = {
    actor ! PoisonPill
  }
}
