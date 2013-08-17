package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait Position extends Ordered[Position] {

  import Position.{Exact, Last}

  def compare_(that: Position) = (this, that) match {
    case (Last, Last) => 0
    case (Last, _) => -1
    case (_, Last) => 1
    case (Exact(c1, p1), Exact(c2, p2)) => (c1 compare c2, p1 compare p2) match {
      case (0, 0) => 0
      case (0, x) => x
      case (x, _) => x
    }
  }
}

object Position {
  val First = Exact(0)

  def apply(position: Long): Position = apply(position, position)

  def apply(commitPosition: Long, preparePosition: Long): Position =
    if (commitPosition == -1 && preparePosition == -1) Last
    else Exact(commitPosition, preparePosition)


  case object Last extends Position {
    def compare(that: Position) = if (that.isInstanceOf[Last.type]) 0 else 1

    override def toString = "LastPosition"
  }


  case class Exact(commitPosition: Long, preparePosition: Long) extends Position {
    require(commitPosition >= 0, s"commitPosition must be >= 0, but is $commitPosition")
    require(preparePosition >= 0, s"preparePosition must be >= 0, but is $preparePosition")
    require(commitPosition >= preparePosition, s"commitPosition must be >= preparePosition, but $commitPosition < $preparePosition ")

    def compare(that: Position) = that match {
      case Last => -1
      case Exact(c, p) => (commitPosition compare c, preparePosition compare p) match {
        case (0, 0) => 0
        case (0, x) => x
        case (x, _) => x
      }
    }

    override def toString =
      if (commitPosition == preparePosition) s"Position($commitPosition)"
      else s"Position($commitPosition,$preparePosition)"
  }

  object Exact {
    def apply(position: Long): Exact = Exact(commitPosition = position, preparePosition = position)
  }

}