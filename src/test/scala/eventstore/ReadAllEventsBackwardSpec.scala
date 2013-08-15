package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsBackwardSpec extends TestConnectionSpec {
  sequential

  implicit val direction = ReadDirection.Backward
  val startPosition = Position.end

  "read all events backward" should {
    "return empty slice if asked to read from start" in new TestConnectionScope {
      readAllEvents(Position.start, 1) must beEmpty
    }

    "return partial slice if not enough events" in new TestConnectionScope {
      val size = Int.MaxValue
      readAllEvents(Position.start, size).size must beLessThan(size)
    }

    "return events in reversed order compared to written" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(startPosition, 10) mustEqual events.reverse
    }

    "be able to read all one by one until end of stream" in new TestConnectionScope {
      readUntilEndOfStream(1)
    }

    "be able to read all slice by slice" in new TestConnectionScope {
      readUntilEndOfStream(5)
    }

    "read 'streamDeleted' events" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(Position.end, 1).head must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new TestConnectionScope {
      doAppendToStream(newEvent, AnyVersion)
      deleteStream()
      readAllEventRecords(startPosition, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }

    "read not modified events" in new TestConnectionScope {
      def read() = readAllEventsSucceed(startPosition, 10)

      val r1 = read()
      val r2 = read()
      r1.resolvedEvents mustEqual r2.resolvedEvents
      r1.modified must beTrue
      r2.modified must beFalse
    }

    "fail to read from wrong position" in new TestConnectionScope {
      val position = readAllEventsSucceed(startPosition, 10).nextPosition
      val wrongPosition = Position(
        commitPosition = position.commitPosition - 1,
        preparePosition = position.preparePosition - 1)
      val failed = readAllEventsFailed(wrongPosition, 10)
      failed.reason mustEqual ReadAllEventsFailed.Error
      failed.message must beSome
    }
  }
}
