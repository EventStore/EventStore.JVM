package eventstore

import akka.testkit.TestProbe
import ExpectedVersion._

class WriteEventsITest extends TestConnection {
  "append to stream" should {
    "not fail for zero events" in new WriteEventsScope {
      writeEventsCompleted(Nil, NoStream)
    }

    "create stream with NoStream exp ver on first write if does not exist" in new WriteEventsScope {
      writeEvent(newEventData, NoStream) must beSome(EventNumber.Range(EventNumber.First))
      streamEvents must haveSize(1)
    }

    "create stream with ANY exp ver on first write if does not exist" in new WriteEventsScope {
      writeEvent(newEventData, Any) must beSome(EventNumber.Range(EventNumber.First))
      streamEvents must haveSize(1)
    }

    "fail create stream with wrong exp ver if does not exist" in new WriteEventsScope {
      writeEventsFailed(newEventData, ExpectedVersion.First) must throwA[WrongExpectedVersionException]
      writeEventsFailed(newEventData, ExpectedVersion(1)) must throwA[WrongExpectedVersionException]
    }

    "fail writing with correct exp ver to deleted stream" in new WriteEventsScope {
      appendEventToCreateStream()
      deleteStream()
      writeEventsFailed(newEventData, ExpectedVersion.First) must throwA[StreamDeletedException]
    }

    "fail writing with any exp ver to deleted stream" in new WriteEventsScope {
      appendEventToCreateStream()
      deleteStream()
      writeEventsFailed(newEventData, Any) must throwA[StreamDeletedException]
    }

    "fail writing with invalid exp ver to deleted stream" in new WriteEventsScope {
      appendEventToCreateStream()
      deleteStream()
      writeEventsFailed(newEventData, ExpectedVersion(1)) must throwA[StreamDeletedException]
    }

    "succeed writing with correct exp ver to existing stream" in new WriteEventsScope {
      appendEventToCreateStream()
      writeEvent(newEventData, ExpectedVersion.First) must beSome(EventNumber.Range(EventNumber.Exact(1)))
      writeEvent(newEventData, ExpectedVersion(1)) must beSome(EventNumber.Range(EventNumber.Exact(2)))
    }

    "succeed writing with any exp ver to existing stream" in new WriteEventsScope {
      appendEventToCreateStream()
      writeEvent(newEventData, Any) must beSome(EventNumber.Range(EventNumber.Exact(1)))
      writeEvent(newEventData, Any) must beSome(EventNumber.Range(EventNumber.Exact(2)))
    }

    "fail writing with wrong exp ver to existing stream" in new WriteEventsScope {
      appendEventToCreateStream()
      writeEventsFailed(newEventData, NoStream) must throwA[WrongExpectedVersionException]
      writeEventsFailed(newEventData, ExpectedVersion(1)) must throwA[WrongExpectedVersionException]
    }

    "be able to append multiple events at once" in new WriteEventsScope {
      val events = appendMany()
      streamEvents mustEqual events
    }

    "be able to append many events at once" in new WriteEventsScope {
      val size = 100
      appendMany(size = size)
      actor ! ReadStreamEvents(streamId, EventNumber.Last, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsCompleted].events.head.number mustEqual EventNumber(size - 1)
      deleteStream()
    }

    "be able to append many events at once concurrently" in new WriteEventsScope {
      val n = 10
      val size = 10

      Seq.fill(n)(TestProbe()).foreach(x => appendMany(size = size, testKit = x))

      actor ! ReadStreamEvents(streamId, EventNumber.Last, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsCompleted].events.head.number mustEqual EventNumber(size * n - 1)

      deleteStream()
    }
  }

  private trait WriteEventsScope extends TestConnectionScope {
    def writeEvent(event: EventData, expVer: ExpectedVersion = Any) = writeEventsCompleted(List(event), expVer)

    def writeEventsFailed(event: EventData, expVer: ExpectedVersion = Any) = {
      actor ! WriteEvents(streamId, List(event), expVer)
      expectEsException()
    }
  }
}
