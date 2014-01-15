package eventstore.util

import java.io.Closeable
import akka.actor.{ PoisonPill, ActorRef }

case class ActorCloseable(actor: ActorRef) extends Closeable {
  def close() = {
    actor ! PoisonPill
  }
}
