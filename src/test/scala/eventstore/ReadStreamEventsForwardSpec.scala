package eventstore

import ReadDirection.Forward

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsForwardSpec extends TestConnectionSpec {
  "read stream events forward" should {
    "fail if count <= 0" in todo

    "fail if start < 0 " in todo

    "fail if stream not found" in new ReadStreamForwardScope {
      failReadStreamEvents(0, 1000, ReadStreamResult.NoStream)
    }

    "fail if stream has been deleted" in new ReadStreamForwardScope {
      appendEventToCreateStream()
      deleteStream()
      failReadStreamEvents(0, 1000, ReadStreamResult.StreamDeleted)
    }

    "return no events when called on empty stream" in new ReadStreamForwardScope {
      doReadStreamEvents(1, Int.MaxValue) must beEmpty
    }

    "get empty slice if asked to read from end" in new ReadStreamForwardScope {
      appendEventToCreateStream()
      doReadStreamEvents(1, 1000) must beEmpty
    }

    "get empty slice if called with non existing range" in new ReadStreamForwardScope {
      appendEventToCreateStream()
      doReadStreamEvents(10, 1000) must beEmpty
    }

    "get partial slice if not enough events in stream" in new ReadStreamForwardScope {
      appendEventToCreateStream()
      doReadStreamEvents(0, 1000) must haveSize(1)
    }

    "get partial slice if not enough events in stream and called with Int.Max count" in new ReadStreamForwardScope {
      appendEventToCreateStream()
      doReadStreamEvents(0, Int.MaxValue) must haveSize(1)
    }

    "get events in same order as written" in new ReadStreamForwardScope {
      val events = (0 to 10).map(_ => newEvent)
      appendMany(events)
      doReadStreamEvents(0, Int.MaxValue) mustEqual events
    }

    "be able to read single event from arbitrary position" in new ReadStreamForwardScope {
      val events = (0 to 10).map(_ => newEvent)
      appendMany(events)
      doReadStreamEvents(5, 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new ReadStreamForwardScope {
      val events = (0 to 10).map(_ => newEvent)
      appendMany(events)
      doReadStreamEvents(5, 3) mustEqual events.slice(5, 8)
    }
  }


  trait ReadStreamForwardScope extends TestConnectionScope {
    def failReadStreamEvents(fromEventNumber: Int, maxCount: Int, expectedResult: ReadStreamResult.Value) {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, resolveLinkTos = false, Forward)
      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, `expectedResult`, _, _, _, _, Forward) => true
      }
    }

    def doReadStreamEvents(fromEventNumber: Int, maxCount: Int): List[Event] = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, resolveLinkTos = false, Forward)
      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, -1, -1, true, _, Forward) => Nil
        case ReadStreamEventsCompleted(xs, ReadStreamResult.Success, next, last, endOfStream, _, Forward) =>
          endOfStream mustEqual (next > last)
          xs.size must beLessThanOrEqualTo(maxCount)
          xs.map(_.eventRecord.event)
      }
    }
  }

}
