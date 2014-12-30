package eventstore
package operations

import eventstore.tcp.PackOut

import scala.util.{ Success, Failure, Try }

sealed trait Decision

object Decision {
  case class Stop(in: Try[In]) extends Decision

  object Stop {
    def apply(x: EsException): Stop = Stop(Failure(x))
    def apply(x: In): Stop = Stop(Success(x))
  }

  case class Retry(operation: Operation, pack: PackOut) extends Decision

  case class Continue(operation: Operation, in: Try[In]) extends Decision

  case object Ignore extends Decision
}
