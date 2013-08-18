package eventstore


/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsForwardSpec extends TestConnectionSpec {
  sequential

  implicit val direction = ReadDirection.Forward
  val startPosition = Position.First

  "read all events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      // TODO server does not validate maxCount, WHY?
      readAllEventsSucceed(Position.First, 0) must throwAn[IllegalArgumentException]
      readAllEventsSucceed(Position.First, -1) must throwAn[IllegalArgumentException]
    }

    "return empty slice if asked to read from end" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(Position.Last, 1) must beEmpty
    }

    "return partial slice if not enough events" in new TestConnectionScope {
      val size = Int.MaxValue
      readAllEvents(Position.First, size).size must beLessThan(size) // TODO
    }

    "return events in same order as written" in new TestConnectionScope {
      val events = appendMany()
      allStreamsEvents().takeRight(events.length).toSeq mustEqual events
    }

    "be able to read all one by one until end of stream" in new TestConnectionScope {
      allStreamsEvents(1).force
    }

    "be able to read all slice by slice" in new TestConnectionScope {
      allStreamsEvents(5).force
    }

    "read 'streamDeleted' events" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(startPosition, Int.MaxValue).last must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(startPosition, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }

    "read not modified events" in new TestConnectionScope {
      def read() = readAllEventsSucceed(startPosition, 10)

      val r1 = read()
      val r2 = read()
      r1.resolvedEvents mustEqual r2.resolvedEvents
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