package eventstore

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope

/**
 * @author Yaroslav Klymko
 */
class PositionSpec extends SpecificationWithJUnit {
  "Position" should {
    ">" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 > p2) mustEqual (p1.commitPosition > p2.commitPosition
          || (p1.commitPosition == p2.commitPosition
          && p1.preparePosition > p2.preparePosition))
      }
    }

    "<" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 < p2) mustEqual (p1.commitPosition < p2.commitPosition
          || (p1.commitPosition == p2.commitPosition
          && p1.preparePosition < p2.preparePosition))
      }
    }

    ">=" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 >= p2) mustEqual (p1 > p2 || p1 == p2)
      }
    }

    "<=" in new PositionScope {
      def verify(p1: Position, p2: Position) {
        (p1 <= p2) mustEqual (p1 < p2 || p1 == p2)
      }
    }
  }

  trait PositionScope extends Scope {
    val values = List(-1, 0, 1)
    val positions = for {
      commitPosition <- values
      preparePosition <- values if commitPosition >= preparePosition
    } yield Position(commitPosition = commitPosition, preparePosition = preparePosition)

    for {
      p1 <- positions
      p2 <- positions
    } verify(p1, p2)

    def verify(p1: Position, p2: Position)
  }
}
