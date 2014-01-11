package eventstore

sealed trait EventNumber extends Ordered[EventNumber]

object EventNumber {
  val First = Exact(0)

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
  }

  object Exact {
    implicit val ordering = Ordering.by[Exact, EventNumber](identity)
  }
}