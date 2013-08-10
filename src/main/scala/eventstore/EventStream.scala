package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait EventStream

case object AllStreams extends EventStream

case class StreamId(id: String) extends EventStream {
  require(id != null, "stream id must be not null")
  require(id.nonEmpty, "stream id must be not empty")

  def isSystem = id.startsWith("$")
  def isMeta = id.startsWith("$$")
}


object EventStream {
  def apply(value: String): EventStream = if (value == null || value == "") AllStreams else StreamId(value)
}