package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsForwardSpec extends TestConnectionSpec {
  implicit val direction = ReadDirection.Forward

  "read stream events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      readStreamEventsFailed(EventNumber.First, 0) must throwAn[IllegalArgumentException]
      readStreamEventsFailed(EventNumber.First, -1) must throwAn[IllegalArgumentException]
    }

    "fail if from == EventNumber.Last " in new TestConnectionScope {
      readStreamEventsSucceed(EventNumber.Last, 1) must throwAn[IllegalArgumentException]
    }

    "fail if stream not found" in new TestConnectionScope {
      readStreamEventsFailed(EventNumber.First, 1000).reason mustEqual ReadStreamEventsFailed.NoStream
    }

    "fail if stream has been deleted" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readStreamEventsFailed(EventNumber.First, 1000).reason mustEqual ReadStreamEventsFailed.StreamDeleted
    }

    "get empty slice if asked to read from end" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(EventNumber(1), 1000) must beEmpty
    }

    "get empty slice if called with non existing range" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(EventNumber(10), 1000) must beEmpty
    }

    "get partial slice if not enough events in stream" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(EventNumber.First, 1000) must haveSize(1)
    }

    "get partial slice if not enough events in stream and called with Int.Max count" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(EventNumber.First, Int.MaxValue) must haveSize(1)
    }

    "get events in same order as written" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber.First, Int.MaxValue) mustEqual events
    }

    "be able to read single event from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber(5), 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber(5), 3) mustEqual events.slice(5, 8)
    }

    "be able to read first event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(EventNumber.First, 1)
      result.resolvedIndexedEvents.map(_.eventRecord.event) mustEqual List(events.head)
      result.endOfStream must beFalse
      result.nextEventNumber mustEqual EventNumber(1)
    }

    "be able to read last event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(EventNumber(9), 1)
      result.resolvedIndexedEvents.map(_.eventRecord.event) mustEqual List(events.last)
      result.endOfStream must beTrue
      result.nextEventNumber mustEqual EventNumber(10)
    }

    "read not modified events" in new TestConnectionScope {
      appendMany()

      def read() = readStreamEventsSucceed(EventNumber.First, 1)

      val r1 = read()
      val r2 = read()
      r1.resolvedIndexedEvents mustEqual r2.resolvedIndexedEvents
    }
  }
}
