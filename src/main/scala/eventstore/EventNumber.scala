package eventstore

/**
 * @author Yaroslav Klymko
 */
// TODO check all "event numbers in messages"
sealed trait EventNumber extends Ordered[EventNumber]// TODO same rules as in Position.First = 0, Position.Last = -1

object EventNumber {
  val First = Exact(0)
  val Max = Exact(Int.MaxValue)

  def apply(eventNumber: Int): Exact = Exact(eventNumber)

  def apply(expectedVersion: ExpectedVersion.Exact): Exact = Exact(expectedVersion.value)


  case object Last extends EventNumber {
    def compare(that: EventNumber) = if (that.isInstanceOf[Last.type]) 0 else 1

    override def toString = "LastEventNumber"
  }

  case class Exact(value: Int) extends EventNumber {
    require(value >= 0, s"event number must be >= 0, but is $value")

    def compare(that: EventNumber) = that match {
      case Last => -1
      case that: Exact => this.value compare that.value
    }

    override def toString = s"EventNumber($value)"
  }

  object Exact {
    implicit val ordering = Ordering.by[Exact, EventNumber](identity)
  }
}