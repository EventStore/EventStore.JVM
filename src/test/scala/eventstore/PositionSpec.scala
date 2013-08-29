package eventstore

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import Position._

/**
 * @author Yaroslav Klymko
 */
class PositionSpec extends Specification {
  "Position" should {
    ">" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 > p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => false
          case (_, Last)                          => false
          case (Last, _)                          => true
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => cp1 > cp2 || (cp1 == cp2 && pp1 > pp2)
        })
      }
    }

    "<" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 < p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => false
          case (_, Last)                          => true
          case (Last, _)                          => false
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => cp1 < cp2 || (cp1 == cp2 && pp1 < pp2)
        })
      }
    }

    ">=" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 >= p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => true
          case (_, Last)                          => false
          case (Last, _)                          => true
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => p1 > p2 || p1 == p2
        })
      }
    }

    "<=" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 <= p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => true
          case (_, Last)                          => true
          case (Last, _)                          => false
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => p1 < p2 || p1 == p2
        })
      }
    }
  }

  trait PositionScope extends Scope {
    val values = List(0, 1, Int.MaxValue)
    val positions = Last :: (for {
      commitPosition <- values
      preparePosition <- values if commitPosition >= preparePosition
    } yield Exact(commitPosition = commitPosition, preparePosition = preparePosition))

    for (p1 <- positions; p2 <- positions) verify(p1, p2)

    def verify(p1: Position, p2: Position)
  }
}
