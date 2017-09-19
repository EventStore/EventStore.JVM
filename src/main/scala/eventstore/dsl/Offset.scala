package eventstore.dsl

import spray.json.{ JsNumber, JsValue, JsonFormat, deserializationError }

class Offset private (val value: Long) extends AnyVal with Ordered[Offset] {

  //noinspection ScalaStyle
  def +(increment: Long): Offset = Offset(this.value + increment)

  //noinspection ScalaStyle
  def -(decrement: Long): Offset = Offset(this.value - decrement)

  //noinspection ScalaStyle
  def +(increment: Int): Offset = Offset(this.value + increment)

  //noinspection ScalaStyle
  def -(decrement: Int): Offset = Offset(this.value - decrement)

  def next: Offset = Offset(value + 1)

  def previous: Option[Offset] = {
    if (this == Offset.First) {
      None
    } else {
      Some(Offset(value - 1))
    }
  }

  def until(exclusive: Offset): IndexedSeq[Offset] = value.until(exclusive.value).map(Offset.apply)

  def compare(that: Offset): Int = value.compare(that.value)

  def min(other: Offset): Offset = {
    if (this < other) this
    else other
  }

  def max(other: Offset): Offset = {
    if (this >= other) this
    else other
  }

  override def toString: String = s"Offset($value)"
}

object Offset {
  val First = Offset(0)
  val MaxValue = Offset(Long.MaxValue)

  def apply(value: Int): Offset = {
    apply(value.toLong)
  }

  def apply(value: Long): Offset = {
    require(value >= 0, s"The offset value : '$value' is not allow")
    new Offset(value)
  }

  def unapply(arg: Offset): Option[Long] = Some(arg.value)

  implicit val offsetJsonFormat = new JsonFormat[Offset] {
    def read(json: JsValue): Offset = json match {
      case JsNumber(value) if value >= 0 && value.isValidLong => Offset(value.toLongExact)
      case JsNumber(value) => deserializationError(s"Offset value must be >=0 but got $value")
      case other => deserializationError(s"Tried to deserialized an Offset while the input is $other")
    }
    def write(obj: Offset): JsValue = JsNumber(obj.value)
  }
}
