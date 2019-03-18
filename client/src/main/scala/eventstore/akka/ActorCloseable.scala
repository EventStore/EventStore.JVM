package eventstore
package akka

import java.io.Closeable
import _root_.akka.actor.{PoisonPill, ActorRef}

final case class ActorCloseable(actor: ActorRef) extends Closeable {
  def close() = {
    actor ! PoisonPill
  }
}
