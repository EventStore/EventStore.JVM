package eventstore

import _root_.akka.actor.Actor

package object akka {

  private[eventstore] def deprecationMsg(name: String) =
    s"$name has been moved from eventstore.$name to eventstore.akka.$name. " +
    s"Please update your imports, as this deprecated type alias will be " +
    s"removed in a future version of EventStore.JVM."

  ///

  def randomUuid: Uuid = eventstore.util.uuid.randomUuid

  implicit class RichPartialFunction(val self: Actor.Receive) extends AnyVal {
    // workaround for https://issues.scala-lang.org/browse/SI-8861
    def or(pf: Actor.Receive): Actor.Receive = self.orElse[Any, Unit](pf)
  }

}
