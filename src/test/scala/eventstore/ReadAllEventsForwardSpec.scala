package eventstore

import OperationResult._
import ReadDirection.Forward


/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsForwardSpec extends TestConnectionSpec {
  sequential

  "read all events forward" should {
    "return empty slice if asked to read from end" in new ReadAllEventsForwardScope {
      val events = (1 to 10).map(_ => newEvent)
      actor ! appendToStream(AnyVersion, events: _ *)
      expectMsg(appendToStreamCompleted())
      readAllEvents(-1, -1, 1) must beEmpty
    }

    "return events in same order as written" in new ReadAllEventsForwardScope {
      val events = (1 to 10).map(_ => newEvent)
      actor ! appendToStream(AnyVersion, events: _ *)
      expectMsg(appendToStreamCompleted())
      readAllEvents(0, 0, Int.MaxValue).takeRight(events.length) mustEqual events
    }

    "be able to read all one by one until end of stream" in new ReadAllEventsForwardScope {
      val size = 1

      def read(commitPosition: Long, preparePosition: Long) {
        actor ! ReadAllEvents(commitPosition, preparePosition, size, resolveLinkTos = false, Forward)
        val (events, nextCommitPosition, nextPreparePosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, _, xs, c, p, Forward) => (xs, c, p)
        }
        if (events.nonEmpty) {
          events must haveSize(size)
          read(nextCommitPosition, nextPreparePosition)
        }
      }

      read(0, 0)
    }

    "be able to read all slice by slice" in new ReadAllEventsForwardScope {
      val size = 5

      def read(commitPosition: Long, preparePosition: Long) {
        actor ! ReadAllEvents(commitPosition, preparePosition, size, resolveLinkTos = false, Forward)
        val (events, nextCommitPosition, nextPreparePosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, _, xs, c, p, Forward) => (xs, c, p)
        }
        if (events.nonEmpty) {
          events.size must beLessThanOrEqualTo(size)
          read(nextCommitPosition, nextPreparePosition)
        }
      }

      read(0, 0)
    }

    "read 'stream-deleted' events" in new ReadAllEventsForwardScope {
      createStream()
      deleteStream()
      readAllEventRecords(0, 0, Int.MaxValue).last must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ok
      }
    }

    "read events from deleted streams" in new ReadAllEventsForwardScope {
      doAppendToStream(newEvent, AnyVersion)
      deleteStream()
      readAllEventRecords(0, 0, Int.MaxValue).filter(_.streamId == streamId) must haveSize(2)
    }
  }

  trait ReadAllEventsForwardScope extends TestConnectionScope {

    def readAllEventRecords(commitPosition: Long, preparePosition: Long, maxCount: Int): List[EventRecord] = {
      actor ! ReadAllEvents(commitPosition, preparePosition, maxCount, resolveLinkTos = false, Forward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Forward) => xs.map(_.event)
      }
    }

    def readAllEvents(commitPosition: Long, preparePosition: Long, maxCount: Int): List[Event] =
      readAllEventRecords(commitPosition, preparePosition, maxCount).map(_.event)
  }
}