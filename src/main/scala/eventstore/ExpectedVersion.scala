package eventstore

/**
 * @author Yaroslav Klymko
 */
sealed trait ExpectedVersion

object ExpectedVersion {
  val First = Exact(0)

  def apply(expectedVersion: Int): Exact = Exact(expectedVersion)

  def apply(eventNumber: EventNumber.Exact): Exact = Exact(eventNumber.value)

  //The stream being written to should not yet exist. If it does exist treat that as a concurrency problem.
  case object NoStream extends ExpectedVersion {
    override def toString = "ExpectedNoStream"
  }

  sealed trait Existing extends ExpectedVersion

  // This write should not conflict with anything and should always succeed.
  case object Any extends Existing {
    override def toString = "ExpectedAnyVersion"
  }

  // States that the last event written to the stream should have a sequence number matching your expected value.
  case class Exact(value: Int) extends Existing {
    require(value >= 0, s"expected version must be >= 0, but is $value}")

    override def toString = s"ExpectedVersion($value)"
  }
}