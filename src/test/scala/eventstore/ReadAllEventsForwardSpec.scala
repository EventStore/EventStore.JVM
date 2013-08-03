package eventstore

import ReadDirection.Forward


/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsForwardSpec extends TestConnectionSpec {
  sequential

  "read all events forward" should {
    "return empty slice if asked to read from end" in new ReadAllEventsForwardScope {
      val events = appendMany()
      readAllEvents(Position.end, 1) must beEmpty
    }

    "return events in same order as written" in new ReadAllEventsForwardScope {
      val events = appendMany()
      readAllEvents(Position.start, Int.MaxValue).takeRight(events.length) mustEqual events
    }

    "be able to read all one by one until end of stream" in new ReadAllEventsForwardScope {
      readUntilEndOfStream(1)
    }

    "be able to read all slice by slice" in new ReadAllEventsForwardScope {
      readUntilEndOfStream(5)
    }

    "read '$streamDeleted' events" in new ReadAllEventsForwardScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(Position.start, Int.MaxValue).last must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new ReadAllEventsForwardScope {
      doAppendToStream(newEvent, AnyVersion)
      deleteStream()
      readAllEventRecords(Position.start, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }
  }

  trait ReadAllEventsForwardScope extends TestConnectionScope {

    def readAllEventRecords(position: Position, maxCount: Int): List[EventRecord] = {
      actor ! ReadAllEvents(position, maxCount, resolveLinkTos = false, Forward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, xs, _, Forward) => xs.map(_.event)
      }
    }

    def readAllEvents(position: Position, maxCount: Int): List[Event] =
      readAllEventRecords(position, maxCount).map(_.event)

    def readUntilEndOfStream(size: Int) {
      def read(position: Position) {
        actor ! ReadAllEvents(position, size, resolveLinkTos = false, Forward)
        val (events, nextPosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, xs, p, Forward) => (xs, p)
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