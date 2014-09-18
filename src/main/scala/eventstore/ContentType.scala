package eventstore

import scala.collection.SortedSet

sealed trait ContentType {
  def value: Int
}

object ContentType {
  val Known: SortedSet[Known] = SortedSet[Known](Binary, Json)(Ordering.by(_.value))

  def apply(value: Int) = Known.find(_.value == value) getOrElse Binary

  sealed trait Known extends ContentType

  case object Binary extends Known {
    def value = 0
    override def toString = "ContentType.Binary"
  }

  case object Json extends Known {
    def value = 1
    override def toString = "ContentType.Json"
  }

  /*TODO not yet implemented in EventStore 3.0.0rc9
  case class Unknown(value: Int) extends ContentType {
    require(value > Known.last.value,
      if (value < Known.head.value) s"content type must be > ${Known.last.value}, but is $value"
      else s"please use ${Known.find(_.value == value).get} instead of $toString")

    override def toString = s"ContentType.Unknown($value)"
  }*/
}