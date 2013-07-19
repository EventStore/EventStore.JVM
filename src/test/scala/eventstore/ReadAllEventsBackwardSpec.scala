package eventstore

import ReadDirection.Backward

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsBackwardSpec extends TestConnectionSpec {
  sequential

  "read all events backward" should {
    "return empty slice if asked to read from start" in new ReadAllEventsBackwardScope {
      readAllEvents(0, 0, 1) must beEmpty
    }

    "return partial slice if not enough events" in new ReadAllEventsBackwardScope {
      val size = Int.MaxValue
      readAllEvents(0, 0, size).size must beLessThan(size)
    }

    "return events in reversed order compared to written" in new ReadAllEventsBackwardScope {
      val events = (1 to 10).map(_ => newEvent)
      appendMany(events)
      readAllEvents(-1, -1, 10) mustEqual events.reverse
    }

    "be able to read all one by one until end of stream" in new ReadAllEventsBackwardScope {
      readUntilEndOfStream(1)
    }

    "be able to read all slice by slice" in new ReadAllEventsBackwardScope {
      readUntilEndOfStream(5)
    }

    "read '$streamDeleted' events" in new ReadAllEventsBackwardScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(-1, -1, 1).head must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new ReadAllEventsBackwardScope {
      doAppendToStream(newEvent, AnyVersion)
      deleteStream()
      readAllEventRecords(-1, -1, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }
  }

  trait ReadAllEventsBackwardScope extends TestConnectionScope {

    def readAllEventRecords(commitPosition: Long, preparePosition: Long, maxCount: Int): List[EventRecord] = {
      actor ! ReadAllEvents(commitPosition, preparePosition, maxCount, resolveLinkTos = false, Backward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Backward) => xs.map(_.event)
      }
    }

    def readAllEvents(commitPosition: Long, preparePosition: Long, maxCount: Int): List[Event] =
      readAllEventRecords(commitPosition, preparePosition, maxCount).map(_.event)

    def readUntilEndOfStream(size: Int) {
      def read(commitPosition: Long, preparePosition: Long) {
        actor ! ReadAllEvents(commitPosition, preparePosition, size, resolveLinkTos = false, Backward)
        val (events, nextCommitPosition, nextPreparePosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, _, xs, c, p, Backward) => (xs, c, p)
        }
        if (events.nonEmpty) {
          events.size must beLessThanOrEqualTo(size)
          read(nextCommitPosition, nextPreparePosition)
        }
      }
      read(0, 0)
    }
  }

}
