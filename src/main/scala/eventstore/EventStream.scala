package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait EventStream

object EventStream {

  object All extends EventStream

  case class Id(value: String) extends EventStream {
    require(value != null, "stream id must be not null")
    require(value.nonEmpty, "stream id must be not empty")
  }

  def apply(value: String): EventStream = if (value == null || value == "") All else Id(value)
}