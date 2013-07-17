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
      actor ! writeEvents(AnyVersion, events: _ *)
      expectMsg(writeEventsCompleted())


      actor ! ReadAllEvents(-1, -1, 1, resolveLinkTos = false, Forward)
      expectMsgPF() {
        case x@ReadAllEventsCompleted(-1, -1, Nil, -1, -1, Forward) =>
          println(x)
          true
      }
    }

    "return events in same order as written" in new ReadAllEventsForwardScope {
      val size = 10
      val events = (1 to size).map(_ => newEvent)
      actor ! writeEvents(AnyVersion, events: _ *)
      expectMsg(writeEventsCompleted())


      val expectedRecords = events.zipWithIndex.map {
        case (x, idx) => eventRecord(idx + 1, x)
      }

      //      ReadAllEventsCompleted(-1,-1,List(),-1,-1,Forward)
      actor ! ReadAllEvents(0, 0, Int.MaxValue, resolveLinkTos = false, Forward)
      // TODO describe READ ALL
      val actualRecords = expectMsgPF() {
        case ReadAllEventsCompleted(0, 0, xs, _, _, Forward) => xs.takeRight(size).map(_.event)
      }
      actualRecords mustEqual expectedRecords
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

    "be able to read all slice by slice"  in new ReadAllEventsForwardScope {
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


    "be able to read 'stream created' events" in new ReadAllEventsForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! ReadAllEvents(0, 0, Int.MaxValue, resolveLinkTos = false, Forward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Forward) => xs.map(_.event)
      }
      //      events.last.eventType mustEqual "$stream-created"
      events.last must beLike {
        case EventRecord(`streamId`, _, _, "$stream-created", _, _) => ok
      }
    }

    "not read 'stream-deleted' events" in new ReadAllEventsForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! ReadAllEvents(0, 0, Int.MaxValue, resolveLinkTos = false, Forward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Forward) => xs.map(_.event)
      }
      events.last must beLike {
        case EventRecord(`streamId`, _, _, "$stream-deleted", _, _) => ko
      }
      //      events.last.eventType mustEqual "$stream-deleted"
    }

    "not read events from deleted streams" in new ReadAllEventsForwardScope {
      actor ! writeEvents(AnyVersion, newEvent)
      expectMsg(writeEventsCompleted())

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! ReadAllEvents(0, 0, Int.MaxValue, resolveLinkTos = false, Forward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Forward) => xs.map(_.event)
      }
      events.filter(_.streamId == streamId) must haveSize(1)
    }
  }

  trait ReadAllEventsForwardScope extends TestConnectionScope

}
