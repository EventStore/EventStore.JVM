package eventstore
package operations

import scala.util.Try

sealed trait OnDisconnected

object OnDisconnected {
  case class Continue(operation: Operation) extends OnDisconnected
  case class Stop(in: Try[In]) extends OnDisconnected
}