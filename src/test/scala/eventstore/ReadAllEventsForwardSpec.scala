package eventstore


/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsForwardSpec extends TestConnectionSpec {
  sequential

  implicit val direction = ReadDirection.Forward
  val startPosition = Position.start

  "read all events forward" should {
    "return empty slice if asked to read from end" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(Position.end, 1) must beEmpty
    }

    "return events in same order as written" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(startPosition, Int.MaxValue).takeRight(events.length) mustEqual events
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
      readAllEventRecords(startPosition, Int.MaxValue).last must beLike {
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