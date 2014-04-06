package eventstore

sealed trait EventNumber extends Ordered[EventNumber]

object EventNumber {
  val First: Exact = Exact(0)

  def start(direction: ReadDirection): EventNumber = direction match {
    case ReadDirection.Forward  => First
    case ReadDirection.Backward => Last
  }

  def apply(eventNumber: Int): Exact = Exact(eventNumber)

  def apply(expectedVersion: ExpectedVersion.Exact): Exact = Exact(expectedVersion.value)

  case object Last extends EventNumber {
    def compare(that: EventNumber) = that match {
      case Last => 0
      case _    => 1
    }

    override def toString = "EventNumber.Last"
  }

  case class Exact(value: Int) extends EventNumber {
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

    def opt(proto: Int): Option[EventNumber.Exact] = if (proto >= 0) Some(Exact(proto)) else None
  }

  case class Range(start: Exact, end: Exact) {
    require(start <= end, s"start must be <= end, but $start > $end")

    override def toString = s"EventNumber.Range(${start.value} to ${end.value})"
  }

  object Range {
    def apply(number: Exact): Range = Range(number, number)

    def opt(first: Int, last: Int): Option[Range] = {
      if (first > last) None
      else for {
        f <- Exact.opt(first)
        l <- Exact.opt(last)
      } yield Range(f, l)
    }
  }
}