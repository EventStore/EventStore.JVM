package eventstore

import akka.actor.ActorRef
import eventstore.util.OneToMany

package object operations {
  type Operations = OneToMany[Operation, Uuid, ActorRef]
}
