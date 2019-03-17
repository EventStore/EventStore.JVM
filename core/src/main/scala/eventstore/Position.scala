package eventstore

sealed trait Position extends Ordered[Position]

object Position {
  val First: Exact = Position.Exact(0)

  def apply(position: Long): Position = Position(position, position)

  def apply(commitPosition: Long, preparePosition: Long): Position = {
    if (commitPosition < 0 || preparePosition < 0) Last
    else Exact(commitPosition, preparePosition)
  }

  def apply(direction: ReadDirection): Position = direction match {
    case ReadDirection.Forward  => First
    case ReadDirection.Backward => Last
  }

  @SerialVersionUID(1L) case object Last extends Position {
    def compare(that: Position) = if (that.isInstanceOf[Last.type]) 0 else 1

    override def toString = "Position.Last"
  }

  @SerialVersionUID(1L) case class Exact(commitPosition: Long, preparePosition: Long) extends Position {
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
    implicit val ordering = Ordering.by[Exact, Position](identity)
    def apply(position: Long): Exact = Exact(commitPosition = position, preparePosition = position)
  }
}