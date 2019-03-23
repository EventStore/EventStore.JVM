package eventstore
package core
package operations

import scala.util.Try

private[eventstore] trait Inspection {
  def expected: Class[_]
  def pf: PartialFunction[Try[In], Inspection.Decision]
}

private[eventstore] object Inspection {
  sealed trait Decision

  object Decision {
    case object Stop extends Decision
    case object Retry extends Decision
    case object Unexpected extends Decision
    final case class Fail(value: EsException) extends Decision
  }
}