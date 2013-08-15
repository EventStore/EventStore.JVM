package eventstore

/**
 * @author Yaroslav Klymko
 */

// TODO hide from eventstore package
sealed trait ExpectedVersion {
  def value: Int
}

// The stream should exist and should be empty. If it does not exist or is not empty treat that as a concurrency problem.
case object EmptyStream extends ExpectedVersion {
  def value = 0
}

//The stream being written to should not yet exist. If it does exist treat that as a concurrency problem.
case object NoStream extends ExpectedVersion {
  def value = -1
}

// This write should not conflict with anything and should always succeed.
case object AnyVersion extends ExpectedVersion {
  def value = -2
}

// States that the last event written to the stream should have a sequence number matching your expected value.
case class Version(value: Int) extends ExpectedVersion {
  require(value > 0, s"version must be > 0, but is $value}")
}


object ExpectedVersion {
  def apply(x: Int): ExpectedVersion = x match {
    case 0 => EmptyStream
    case -1 => NoStream
    case -2 => AnyVersion
    case version => Version(version)
  }
}