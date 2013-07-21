package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait EventNumber

object EventNumber {
  val First = Exact(0)
  val Max = Exact(Int.MaxValue)
  object Last extends EventNumber

  def apply(eventNumber: Int): EventNumber = {
    if (eventNumber == First.value) First
    else if (eventNumber > 0) Exact(eventNumber)
    else if (eventNumber == Max.value) Max
    else NoEvent
  }

  case class Exact(value: Int) extends EventNumber {
    require(value >= 0, s"event number must be >= 0, but is $value")
  }

  case object NoEvent extends EventNumber
}