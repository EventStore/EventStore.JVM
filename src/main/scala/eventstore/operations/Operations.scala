package eventstore
package operations

import akka.actor.ActorRef
import eventstore.util.OneToMany

object Operations {
  val Empty: Operations = OneToMany[Operation, Uuid, ActorRef](_.id, _.client)
}