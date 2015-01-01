package eventstore
package operations

import eventstore.tcp.PackOut

import scala.util.Try

sealed trait OnOutgoing

object OnOutgoing {
  case class Stop(out: PackOut, in: Try[In]) extends OnOutgoing
  case class Continue(operation: Operation, out: PackOut) extends OnOutgoing
}