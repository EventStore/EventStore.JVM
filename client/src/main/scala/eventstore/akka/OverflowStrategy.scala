package eventstore
package akka

import _root_.akka.stream.{ OverflowStrategy => Akka }

sealed trait OverflowStrategy {
  def toAkka: _root_.akka.stream.OverflowStrategy
}

object OverflowStrategy {
  lazy val Values: Set[OverflowStrategy] = Set(DropHead, DropTail, DropBuffer, DropNew, Fail)
  private lazy val map = Values.map(x => (x.toString.toLowerCase, x)).toMap

  def apply(x: String): OverflowStrategy = map getOrElse (x.toLowerCase, sys error s"No OverflowStrategy found by $x")

  case object DropHead extends OverflowStrategy { def toAkka = Akka.dropHead }
  case object DropTail extends OverflowStrategy { def toAkka = Akka.dropTail }
  case object DropBuffer extends OverflowStrategy { def toAkka = Akka.dropBuffer }
  case object DropNew extends OverflowStrategy { def toAkka = Akka.dropNew }
  case object Fail extends OverflowStrategy { def toAkka = Akka.fail }
}