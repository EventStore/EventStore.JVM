package eventstore

/**
 * @author Yaroslav Klymko
 */
case class Position(commitPosition: Long, preparePosition: Long) extends Ordered[Position] {
  require(commitPosition >= -1, s"commitPosition must be >= -1, but is $commitPosition")
  require(preparePosition >= -1, s"preparePosition must be >= -1, but is $preparePosition")

  def compare(that: Position) =
    (this.commitPosition compare that.commitPosition, this.preparePosition compare that.preparePosition) match {
      case (0, 0) => 0
      case (0, x) => x
      case (x, _) => x
    }
}

object Position {
  val start = Position(0, 0)
  val end = Position(-1, -1)
}