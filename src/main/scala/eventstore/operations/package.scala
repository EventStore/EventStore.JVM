package eventstore

import akka.actor.ActorRef
import eventstore.tcp.TcpPackageOut
import eventstore.util.OneToMany

import scala.util.Try

package object operations {
  type Operations = OneToMany[Operation, Uuid, ActorRef]
  type OutFunc = TcpPackageOut => Unit
  type InFunc = Try[In] => Unit
}
