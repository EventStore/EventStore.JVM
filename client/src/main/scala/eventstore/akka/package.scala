package eventstore

import _root_.akka.actor.Actor

package object akka {

  def randomUuid: Uuid = eventstore.util.uuid.randomUuid

  implicit class RichPartialFunction(val self: Actor.Receive) extends AnyVal {
    // workaround for https://issues.scala-lang.org/browse/SI-8861
    def or(pf: Actor.Receive): Actor.Receive = self.orElse[Any, Unit](pf)
  }

}
