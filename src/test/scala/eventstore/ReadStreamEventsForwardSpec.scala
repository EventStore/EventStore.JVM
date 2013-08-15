package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsForwardSpec extends TestConnectionSpec {
  implicit val direction = ReadDirection.Forward

  "read stream events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      readStreamEventsFailed(0, 0) must throwAn[IllegalArgumentException]
      readStreamEventsFailed(0, -1) must throwAn[IllegalArgumentException]
    }

    "fail if start < 0 " in new TestConnectionScope {
      readStreamEventsFailed(-1, 1) must throwAn[IllegalArgumentException]
    }

    "fail if stream not found" in new TestConnectionScope {
      readStreamEventsFailed(0, 1000).reason mustEqual ReadStreamEventsFailed.NoStream
    }

    "fail if stream has been deleted" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readStreamEventsFailed(0, 1000).reason mustEqual ReadStreamEventsFailed.StreamDeleted
    }

    "get empty slice if asked to read from end" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(1, 1000) must beEmpty
    }

    "get empty slice if called with non existing range" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(10, 1000) must beEmpty
    }

    "get partial slice if not enough events in stream" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(0, 1000) must haveSize(1)
    }

    "get partial slice if not enough events in stream and called with Int.Max count" in new TestConnectionScope {
      appendEventToCreateStream()
      readStreamEvents(0, Int.MaxValue) must haveSize(1)
    }

    "get events in same order as written" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(0, Int.MaxValue) mustEqual events
    }

    "be able to read single event from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(5, 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(5, 3) mustEqual events.slice(5, 8)
    }

    "be able to read first event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(0, 1)
      result.events.map(_.eventRecord.event) mustEqual List(events.head)
      result.endOfStream must beFalse
      result.nextEventNumber mustEqual 1
    }

    "be able to read last event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsSucceed(9, 1)
      result.events.map(_.eventRecord.event) mustEqual List(events.last)
      result.endOfStream must beTrue
      result.nextEventNumber mustEqual 10
    }

    "read not modified events" in new TestConnectionScope {
//      appendMany()
//
//      def read() = readStreamEventsSucceed(0, 1)
//
//      val r1 = read()
//      val r2 = read()
//      r1.events mustEqual r2.events
//      r1.modified must beTrue
//      r2.modified must beFalse
      skipped
    }

    "fail to read from wrong position" in new TestConnectionScope {
      appendMany()
      println(readStreamEventsFailed(-1, 1)) // TODO check on client side
      //      val result = readStreamEventsSucceed(-1, 1)
      //      println(result)
    }
  }
}
