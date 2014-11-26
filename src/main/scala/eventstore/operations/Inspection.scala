package eventstore
package operations

import scala.util.{ Failure, Success, Try }

trait Inspection {
  def expected: Class[_]
  def pf: PartialFunction[Try[In], Inspection.Decision]
}

object Inspection {
  sealed trait Decision

  object Decision {
    case object Stop extends Decision
    case object Retry extends Decision
    case object Unexpected extends Decision
    case class Fail(value: EsException) extends Decision
  }
}