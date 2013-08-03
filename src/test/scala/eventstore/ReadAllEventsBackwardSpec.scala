package eventstore

import ReadDirection.Backward

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsBackwardSpec extends TestConnectionSpec {
  sequential

  "read all events backward" should {
    "return empty slice if asked to read from start" in new ReadAllEventsBackwardScope {
      readAllEvents(Position.start, 1) must beEmpty
    }

    "return partial slice if not enough events" in new ReadAllEventsBackwardScope {
      val size = Int.MaxValue
      readAllEvents(Position.start, size).size must beLessThan(size)
    }

    "return events in reversed order compared to written" in new ReadAllEventsBackwardScope {
      val events = appendMany()
      readAllEvents(Position.end, 10) mustEqual events.reverse
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
      readAllEventRecords(Position.end, 1).head must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new ReadAllEventsBackwardScope {
      doAppendToStream(newEvent, AnyVersion)
      deleteStream()
      readAllEventRecords(Position.end, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }
  }

  trait ReadAllEventsBackwardScope extends TestConnectionScope {

    def readAllEventRecords(position: Position, maxCount: Int): List[EventRecord] = {
      actor ! ReadAllEvents(position, maxCount, resolveLinkTos = false, Backward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, xs, _, Backward) => xs.map(_.event)
      }
    }

    def readAllEvents(position: Position, maxCount: Int): List[Event] =
      readAllEventRecords(position, maxCount).map(_.event)

    def readUntilEndOfStream(size: Int) {
      def read(position: Position) {
        actor ! ReadAllEvents(position, size, resolveLinkTos = false, Backward)
        val (events, nextPosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, xs, p, Backward) => (xs, p)
        }
        if (events.nonEmpty) {
          events.size must beLessThanOrEqualTo(size)
          read(nextPosition)
        }
      }
      read(Position.start)
    }
  }

}
