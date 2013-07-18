package eventstore

import ReadDirection.Backward

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsBackwardSpec extends TestConnectionSpec {
  sequential
  "read all events backward" should {
    "return empty slice if asked to read from start" in new ReadAllEventsBackwardScope {
      //      prepareStream

      actor ! ReadAllEvents(0, 0, 1, resolveLinkTos = false, Backward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, _, Nil, _, _, Backward) => true
      }
    }

    "return partial slice if not enough events" in new ReadAllEventsBackwardScope {
      val size = Int.MaxValue
      actor ! ReadAllEvents(0, 0, size, resolveLinkTos = false, Backward)
      expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Backward) => xs.size
      } must beLessThan(size)
    }


    "return events in reversed order compared to written" in new ReadAllEventsBackwardScope {
      val size = 10
      val events = (1 to size).map(_ => newEvent)
      actor ! appendToStream(AnyVersion, events: _ *)
      expectMsg(appendToStreamCompleted())


      val expectedRecords = events.zipWithIndex.map {
        case (x, idx) => eventRecord(idx + 1, x)
      }

      //      ReadAllEventsCompleted(-1,-1,List(),-1,-1,Forward)
      actor ! ReadAllEvents(-1, -1, size, resolveLinkTos = false, Backward)
      // TODO describe READ ALL
      val actualRecords = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Backward) => xs.takeRight(size).map(_.event)
      }
      actualRecords.reverse mustEqual expectedRecords

    }

    /* TODO "be able to read 'stream created' events" in new ReadAllEventsBackwardScope {
      createStream

      actor ! ReadAllEvents(-1, -1, 1, resolveLinkTos = false, Backward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, x :: Nil, _, _, Backward) => x.event
      } must beLike {
        case EventRecord(`streamId`, _, _, "$stream-created", _, _) => ok
      }
    }*/

    "be able to read all one by one until end of stream" in new ReadAllEventsBackwardScope {
      val size = 1

      def read(commitPosition: Long, preparePosition: Long) {
        actor ! ReadAllEvents(commitPosition, preparePosition, size, resolveLinkTos = false, Backward)
        val (events, nextCommitPosition, nextPreparePosition) = expectMsgPF() {
          case ReadAllEventsCompleted(_, _, xs, c, p, Backward) => (xs, c, p)
        }
        if (events.nonEmpty) {
          events must haveSize(size)
          read(nextCommitPosition, nextPreparePosition)
        }
      }

      read(-1, -1)
    }

    "be able to read all slice by slice" in new ReadAllEventsBackwardScope {
      val size = 5

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

      read(-1, -1)
    }

   "not read 'stream-deleted' events" in new ReadAllEventsBackwardScope {
      createStream()

      deleteStream()

      actor ! ReadAllEvents(-1, -1, Int.MaxValue, resolveLinkTos = false, Backward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Backward) => xs.map(_.event)
      }
      events.last must beLike {
        case EventRecord(`streamId`, _, Event.StreamDeleted(_)) => ko
      }
    }

    "not read events from deleted streams" in new ReadAllEventsBackwardScope {
      actor ! appendToStream(AnyVersion, newEvent)
      expectMsg(appendToStreamCompleted())

      deleteStream()

      actor ! ReadAllEvents(-1, -1, Int.MaxValue, resolveLinkTos = false, Backward)
      val events = expectMsgPF() {
        case ReadAllEventsCompleted(_, _, xs, _, _, Backward) => xs.map(_.event)
      }
      events.filter(_.streamId == streamId) must haveSize(1)
    }
  }

  trait ReadAllEventsBackwardScope extends TestConnectionScope

}
