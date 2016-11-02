package eventstore

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import Position._

class PositionSpec extends Specification {
  "Position" should {
    ">" in new PositionScope {
      def verify(p1: Position, p2: Position) = {
        (p1 > p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => false
          case (_, Last)                          => false
          case (Last, _)                          => true
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => cp1 > cp2 || (cp1 == cp2 && pp1 > pp2)
        })
      }
    }

    "<" in new PositionScope {
      def verify(p1: Position, p2: Position) = {
        (p1 < p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => false
          case (_, Last)                          => true
          case (Last, _)                          => false
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => cp1 < cp2 || (cp1 == cp2 && pp1 < pp2)
        })
      }
    }

    ">=" in new PositionScope {
      def verify(p1: Position, p2: Position) = {
        (p1 >= p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => true
          case (_, Last)                          => false
          case (Last, _)                          => true
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => p1 > p2 || p1 == p2
        })
      }
    }

    "<=" in new PositionScope {
      def verify(p1: Position, p2: Position) = {
        (p1 <= p2) mustEqual ((p1, p2) match {
          case (Last, Last)                       => true
          case (_, Last)                          => true
          case (Last, _)                          => false
          case (Exact(cp1, pp1), Exact(cp2, pp2)) => p1 < p2 || p1 == p2
        })
      }
    }

    "return First for Forward" in {
      Position(ReadDirection.Forward) mustEqual Position.First
    }

    "return Last for Backward" in {
      Position(ReadDirection.Backward) mustEqual Position.Last
    }

    "return Last for position < 0" in {
      Position(-1) mustEqual Position.Last
      Position(0, -1) mustEqual Position.Last
    }

    "return position with commit equal to prepare" in {
      Position(1) mustEqual Position(1, 1)
    }
  }

  "Position.Exact" should {
    "throw exception if commitPosition < 0" in {
      Position.Exact(-1) must throwAn[IllegalArgumentException]
    }

    "throw exception if preparePosition < 0" in {
      Position.Exact(1, -1) must throwAn[IllegalArgumentException]
    }

    "throw exception if commitPosition < preparePosition" in {
      Position.Exact(0, 1) must throwAn[IllegalArgumentException]
    }

    "return position with commit equal to prepare" in {
      Position.Exact(1) mustEqual Position.Exact(1, 1)
    }

    "have readable toString" in {
      Position.First.toString mustEqual "Position(0)"
      Position(1, 0).toString mustEqual "Position(1,0)"
    }
  }

  "Position.Last" should {
    "have readable toString" in {
      Position.Last.toString mustEqual "Position.Last"
    }
  }

  private trait PositionScope extends Scope {
    val values = List[Long](0L, 1L, Int.MaxValue)
    val positions = Last :: (for {
      commitPosition <- values
      preparePosition <- values if commitPosition >= preparePosition
    } yield Exact(commitPosition = commitPosition, preparePosition = preparePosition))

    for (p1 <- positions; p2 <- positions) verify(p1, p2)

    def verify(p1: Position, p2: Position): Unit
  }
}
