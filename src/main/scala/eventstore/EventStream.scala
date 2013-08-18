package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait EventStream

object EventStream {

  def apply(id: String): Id = Id(id)

  case object All extends EventStream {
    override def toString = "AllEventStreams"
  }

  case class Id(value: String) extends EventStream {
    require(value != null, "stream id must be not null")
    require(value.nonEmpty, "stream id must be not empty")

    def isSystem = value.startsWith("$")
    def isMeta = value.startsWith("$$")

    override def toString = s"EventStreamId($value)"
  }
}