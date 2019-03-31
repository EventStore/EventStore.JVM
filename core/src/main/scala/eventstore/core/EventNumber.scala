package eventstore
package core

sealed trait EventNumber extends Ordered[EventNumber]

object EventNumber {
  val First: Exact = Exact(0)
  val Current: Last.type = Last

  def apply(direction: ReadDirection): EventNumber = direction match {
    case ReadDirection.Forward  => First
    case ReadDirection.Backward => Last
  }

  def apply(eventNumber: Long): EventNumber = if (eventNumber < 0) Last else Exact(eventNumber)

  @SerialVersionUID(1L) case object Last extends EventNumber {
    def compare(that: EventNumber) = that match {
      case Last => 0
      case _    => 1
    }

    override def toString = "EventNumber.Last"
  }

  @SerialVersionUID(1L) final case class Exact(value: Long) extends EventNumber {
    require(value >= 0, s"event number must be >= 0, but is $value")

    def compare(that: EventNumber) = that match {
      case Last        => -1
      case that: Exact => this.value compare that.value
    }

    override def toString = s"EventNumber($value)"

    def to(last: EventNumber.Exact): Range = Range(this, last)
  }

  object Exact {
    implicit val ordering = Ordering.by[Exact, EventNumber](identity)

    def apply(expectedVersion: ExpectedVersion.Exact): Exact = Exact(expectedVersion.value)

    def opt(proto: Long): Option[EventNumber.Exact] = if (proto >= 0) Some(Exact(proto)) else None
  }

  @SerialVersionUID(1L) final case class Range(start: Exact, end: Exact) {
    require(start <= end, s"start must be <= end, but $start > $end")

    override def toString = s"EventNumber.Range(${start.value} to ${end.value})"
  }

  object Range {
    def apply(number: Exact): Range = Range(number, number)

    def apply(start: Long, end: Long): Range = Range(Exact(start), Exact(end))

    def apply(start: Long): Range = Range(Exact(start))

    def opt(first: Long, last: Long): Option[Range] = {
      if (first > last) None
      else for {
        f <- Exact.opt(first)
        l <- Exact.opt(last)
      } yield Range(f, l)
    }
  }
}