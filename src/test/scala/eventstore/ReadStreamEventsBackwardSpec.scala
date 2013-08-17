package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsBackwardSpec extends TestConnectionSpec {
  implicit val direction = ReadDirection.Backward

  "read stream events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      readStreamEventsFailed(0, 0) must throwAn[IllegalArgumentException]
      readStreamEventsFailed(0, -1) must throwAn[IllegalArgumentException]
    }

    "fail if stream not found" in new TestConnectionScope {
      readStreamEventsFailed(0, 1000).reason mustEqual ReadStreamEventsFailed.NoStream
    }

    "fail if stream has been deleted" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readStreamEventsFailed(0, 1000).reason mustEqual ReadStreamEventsFailed.StreamDeleted
    }

    "get empty slice if called with non existing range" in new TestConnectionScope {
      append(newEvent, newEvent)
      readStreamEvents(1000, 10) must beEmpty
    }

    "get partial slice if not enough events in stream" in new TestConnectionScope {
      append(newEvent, newEvent)
      readStreamEvents(1, 1000) must haveSize(2)
    }

    "get events in reversed order as written" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(-1, 10) mustEqual events.reverse
    }

    "be able to read single event from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(5, 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(5, 3) mustEqual List(events(5), events(4), events(3))
    }

    "be able to read first event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(0, 1)
      result.resolvedIndexedEvents.map(_.eventRecord.event) mustEqual List(events.head)
      result.endOfStream must beTrue
      result.nextEventNumber mustEqual -1
    }

    "be able to read last event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(9, 1)
      result.resolvedIndexedEvents.map(_.eventRecord.event) mustEqual List(events.last)
      result.endOfStream must beFalse
      result.nextEventNumber mustEqual 8
    }

    "read not modified events" in new TestConnectionScope {
      appendMany()

      def read() = readStreamEventsSucceed(0, 1)

      val r1 = read()
      val r2 = read()
      r1.resolvedIndexedEvents mustEqual r2.resolvedIndexedEvents
    }
  }
}
