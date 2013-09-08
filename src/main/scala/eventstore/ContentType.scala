package eventstore

import scala.collection.SortedSet

/**
 * @author Yaroslav Klymko
 */
// TODO not yet implemented in EventStore 2.0.1
sealed trait ContentType {
  def value: Int
}

object ContentType {
  val Known: SortedSet[Known] = SortedSet[Known](Binary, Json)(Ordering.by(_.value))

  def apply(value: Int) = Known.find(_.value == value) getOrElse Unknown(value)

  sealed trait Known extends ContentType

  case object Binary extends Known {
    def value = 0
    override def toString = "ContentType.Binary"
  }

  case object Json extends Known {
    def value = 1
    override def toString = "ContentType.Json"
  }

  case class Unknown(value: Int) extends ContentType {
    require(value > Known.last.value,
      if (value < Known.head.value) s"content type must be > ${Known.last.value}, but is $value"
      else s"please use ${Known.find(_.value == value).get} instead of $toString")

    override def toString = s"ContentType.Unknown($value)"
  }
}