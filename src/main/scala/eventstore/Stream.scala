package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait Stream

object Stream {

  object All extends Stream

  case class Id(value: String) extends Stream {
    require(value != null, "stream id must be not null")
    require(value.nonEmpty, "stream id must be not empty")
  }

  def apply(value: String): Stream = if (value == null || value == "") All else Id(value)
}