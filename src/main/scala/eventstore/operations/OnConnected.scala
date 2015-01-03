package eventstore
package operations

import eventstore.tcp.PackOut

import scala.util.Try

sealed trait OnConnected

object OnConnected {
  case class Retry(operation: Operation, out: PackOut) extends OnConnected
  case class Stop(in: Try[In]) extends OnConnected
}