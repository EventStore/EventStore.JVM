package eventstore

/**
 * Result type returned after writing to a stream.
 *
 * @param nextExpectedVersion The next expected version for the stream.
 * @param logPosition The position of the write in the log
 */
case class WriteResult(nextExpectedVersion: ExpectedVersion.Exact, logPosition: Position)

object WriteResult {
  def opt(x: WriteEventsCompleted): Option[WriteResult] = for {
    r <- x.numbersRange
    p <- x.position
  } yield WriteResult(ExpectedVersion.Exact(r.end), p)
}

/**
 * Result type returned after deleting a stream.
 *
 * @param logPosition The position of the write in the log
 */
case class DeleteResult(logPosition: Position)