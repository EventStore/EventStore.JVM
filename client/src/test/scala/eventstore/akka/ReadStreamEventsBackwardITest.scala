package eventstore
package akka

import core.constants.MaxBatchSize

class ReadStreamEventsBackwardITest extends TestConnection {
  implicit val direction: ReadDirection = ReadDirection.Backward

  "read stream events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      readStreamEventsFailed(EventNumber.Last, 0) must throwAn[IllegalArgumentException]
      readStreamEventsFailed(EventNumber.Last, -1) must throwAn[IllegalArgumentException]
    }

    "fail if count > MaxBatchSize" in new TestConnectionScope {
      readStreamEventsFailed(EventNumber.Last, MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "fail if stream not found" in new TestConnectionScope {
      readStreamEventsFailed(EventNumber.Last, 1000) must throwA[StreamNotFoundException]
    }

    "fail if stream has been deleted" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readStreamEventsFailed(EventNumber.Last, 1000) must throwA[StreamDeletedException]
    }

    "read last number if stream truncated" in new TestConnectionScope {
      appendEventToCreateStream()
      truncateStream(EventNumber.Exact(1))
      readStreamEventsCompleted(EventNumber.Last, 1).lastEventNumber mustEqual EventNumber.First
    }

    "read last number if stream truncated many" in new TestConnectionScope {
      appendMany(10)
      truncateStream(EventNumber.Exact(15))

      actor ! ReadEvent.StreamMetadata(streamId.metadata)
      expectMsgType[ReadEventCompleted]

      readStreamEventsCompleted(EventNumber.Last, 1).lastEventNumber mustEqual EventNumber.Exact(9)

      val events = appendMany(10).takeRight(5)
      val result = readStreamEventsCompleted(EventNumber.Last, 10)
      result.events.size mustEqual 5
      result.events.map { _.data } mustEqual events.reverse
    }

    "get empty slice if called with non existing range" in new TestConnectionScope {
      append(newEventData)
      readStreamEvents(EventNumber(1000), 10) must beEmpty
    }

    "get partial slice if not enough events in stream" in new TestConnectionScope {
      append(newEventData)
      readStreamEvents(EventNumber(0), 1000) must haveSize(1)
    }

    "get events in reversed order as written" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber.Last, 10) mustEqual events.reverse
    }

    "be able to read single event from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber(5), 1) mustEqual List(events(5))
    }

    "be able to read slice from arbitrary position" in new TestConnectionScope {
      val events = appendMany()
      readStreamEvents(EventNumber(5), 3) mustEqual List(events(5), events(4), events(3))
    }

    "be able to read first event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsCompleted(EventNumber.First, 1)
      result.events.map(_.data) mustEqual List(events.head)
      result.endOfStream must beTrue
      result.nextEventNumber mustEqual EventNumber.Last
    }

    "be able to read last event" in new TestConnectionScope {
      val events = appendMany()
      val result = readStreamEventsCompleted(EventNumber(9), 1)
      result.events.map(_.data) mustEqual List(events.last)
      result.endOfStream must beFalse
      result.nextEventNumber mustEqual EventNumber(8)
    }

    "read not modified events" in new TestConnectionScope {
      appendMany()

      def read() = readStreamEventsCompleted(EventNumber.First, 1)

      val r1 = read()
      val r2 = read()
      r1.events mustEqual r2.events
    }

    "not read linked events if resolveLinkTos = false" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val event = readStreamEventsCompleted(EventNumber.Last, 5, resolveLinkTos = false).events.head
      event mustEqual link
    }

    "read linked events if resolveLinkTos = true" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val event = readStreamEventsCompleted(EventNumber.Last, 5, resolveLinkTos = true).events.head
      event mustEqual ResolvedEvent(linked, link)
    }
  }
}
