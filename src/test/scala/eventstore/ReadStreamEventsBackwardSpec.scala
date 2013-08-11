package eventstore

import ReadDirection.Backward

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsBackwardSpec extends TestConnectionSpec {
  "read stream events forward" should {
    "fail if count <= 0" in todo

    "fail if stream not found" in new ReadStreamBackwardScope {
      failReadStreamEvents(0, 1000, ReadStreamResult.NoStream)
    }

    "fail if stream has been deleted" in new ReadStreamBackwardScope {
      appendEventToCreateStream()
      deleteStream()
      failReadStreamEvents(0, 1000, ReadStreamResult.StreamDeleted)
    }

    "get no events if stream is empty" in new ReadStreamBackwardScope {
      doReadStreamEvents(5, 1) must beEmpty
    }

    "get empty slice if called with non existing range" in new ReadStreamBackwardScope {
      append(newEvent, newEvent)
      doReadStreamEvents(1000, 10) must beEmpty
    }

    "get partial slice if not enough events in stream" in new ReadStreamBackwardScope {
      append(newEvent, newEvent)
      doReadStreamEvents(1, 1000) must haveSize(2)
    }

    "get events in reversed order as written" in new ReadStreamBackwardScope {
      val events = appendMany()
      doReadStreamEvents(-1, 10) mustEqual events.reverse
    }

    "be able to read single event from arbitrary position" in new ReadStreamBackwardScope {
      val events = appendMany()
      doReadStreamEvents(5, 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new ReadStreamBackwardScope {
      val events = appendMany()
      doReadStreamEvents(5, 3) mustEqual List(events(5), events(4), events(3))
    }

    "be able to read first event" in new ReadStreamBackwardScope {
      val events = appendMany()
      doReadStreamEvents(0, 1) mustEqual List(events.head)
    }

    "be able to read last event" in new ReadStreamBackwardScope {
      val events = appendMany()
      doReadStreamEvents(9, 1) mustEqual List(events.last)
    }
  }

  trait ReadStreamBackwardScope extends TestConnectionScope{
    def failReadStreamEvents(fromEventNumber: Int, maxCount: Int, expectedResult: ReadStreamResult.Value) {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, resolveLinkTos = false, Backward)
      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, `expectedResult`, _, _, _, _, Backward) => true
      }
    }

    def doReadStreamEvents(fromEventNumber: Int, maxCount: Int): Seq[Event] = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, resolveLinkTos = false, Backward)
      val next = Math.max(-1, fromEventNumber - maxCount)
      val endOfStream = next == -1
      expectMsgPF() {
        case ReadStreamEventsCompleted(xs, ReadStreamResult.Success, `next`, _, `endOfStream`, _, Backward) => xs.map(_.eventRecord.event)
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, -1, -1, true, _, Backward) => Nil
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.Success, x, last, `endOfStream`, _, Backward) if fromEventNumber > last && x == last => Nil
      }
    }
  }
}
