package eventstore
package akka

import _root_.akka.stream.{ OverflowStrategy => AOS }

sealed trait OverflowStrategy
object OverflowStrategy {

  lazy val Values: Set[OverflowStrategy] = Set(DropHead, DropTail, DropBuffer, DropNew, Fail)
  private lazy val map = Values.map(x => (x.toString.toLowerCase, x)).toMap

  def apply(x: String): OverflowStrategy =
    map getOrElse (x.toLowerCase, sys error s"No OverflowStrategy found by $x")

  case object DropHead   extends OverflowStrategy
  case object DropTail   extends OverflowStrategy
  case object DropBuffer extends OverflowStrategy
  case object DropNew    extends OverflowStrategy
  case object Fail       extends OverflowStrategy

  ///

  private[eventstore] implicit class OverflowStrategyOps(os: OverflowStrategy) {
    def toAkka: AOS = os match {
      case DropHead   => AOS.dropHead
      case DropTail   => AOS.dropTail
      case DropBuffer => AOS.dropBuffer
      case DropNew    => AOS.dropNew
      case Fail       => AOS.fail
    }
  }
}