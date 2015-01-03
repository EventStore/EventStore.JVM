package eventstore
package operations

import eventstore.tcp.PackOut
import scala.util.{ Failure, Try }

sealed trait OnIncoming

object OnIncoming {
  case class Stop(in: Try[In]) extends OnIncoming

  object Stop {
    def apply(x: EsException): Stop = Stop(Failure(x))
    def apply(x: In): Stop = Stop(Try(x))
  }

  case class Retry(operation: Operation, pack: PackOut) extends OnIncoming

  case class Continue(operation: Operation, in: Try[In]) extends OnIncoming

  case object Ignore extends OnIncoming
}
