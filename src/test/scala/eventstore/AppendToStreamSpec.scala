package eventstore

import akka.testkit.TestProbe
import ExpectedVersion._
import OperationFailed._

/**
 * @author Yaroslav Klymko
 */
// TODO improve expectMsgType[ReadStreamEventsSucceed]
class AppendToStreamSpec extends TestConnectionSpec {
  "append to stream" should {
    "succeed for zero events" in new AppendToStreamScope {
      appendToStreamSucceed(Nil, NoStream) mustEqual EventNumber.First
    }

    "create stream with NoStream exp ver on first write if does not exist" in new AppendToStreamScope {
      append(newEventData, NoStream) mustEqual EventNumber.First
      streamEvents must haveSize(1)
    }

    "create stream with ANY exp ver on first write if does not exist" in new AppendToStreamScope {
      append(newEventData, Any) mustEqual EventNumber.First
      streamEvents must haveSize(1)
    }

    "fail create stream with wrong exp ver if does not exist" in new AppendToStreamScope {
      appendToStreamFailed(newEventData, ExpectedVersion.First) mustEqual WrongExpectedVersion
      appendToStreamFailed(newEventData, ExpectedVersion(1)) mustEqual WrongExpectedVersion
    }

    "fail writing with correct exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      appendToStreamFailed(newEventData, ExpectedVersion.First) mustEqual StreamDeleted
    }

    "fail writing with any exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      appendToStreamFailed(newEventData, Any) mustEqual StreamDeleted
    }

    "fail writing with invalid exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      appendToStreamFailed(newEventData, ExpectedVersion(1)) mustEqual StreamDeleted
    }

    "succeed writing with correct exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      append(newEventData, ExpectedVersion.First) mustEqual EventNumber(1)
      append(newEventData, ExpectedVersion(1)) mustEqual EventNumber(2)
    }

    "succeed writing with any exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      append(newEventData, Any) mustEqual EventNumber(1)
      append(newEventData, Any) mustEqual EventNumber(2)
    }

    "fail writing with wrong exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      appendToStreamFailed(newEventData, NoStream) mustEqual WrongExpectedVersion
      appendToStreamFailed(newEventData, ExpectedVersion(1)) mustEqual WrongExpectedVersion
    }

    "be able to append multiple events at once" in new AppendToStreamScope {
      val events = appendMany()
      streamEvents mustEqual events
    }

    "be able to append many events at once" in new AppendToStreamScope {
      val size = 100
      appendMany(size = size)
      actor ! ReadStreamEvents(streamId, EventNumber.Last, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsSucceed].events.head.number mustEqual EventNumber(size - 1)
      deleteStream()
    }

    "be able to append many events at once concurrently" in new AppendToStreamScope {
      val n = 10
      val size = 10

      Seq.fill(n)(TestProbe()).foreach(x => appendMany(size = size, testKit = x))

      actor ! ReadStreamEvents(streamId, EventNumber.Last, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsSucceed].events.head.number mustEqual EventNumber(size * n - 1)

      deleteStream()
    }
  }

  trait AppendToStreamScope extends TestConnectionScope {
    def append(event: EventData, expVer: ExpectedVersion = Any) = appendToStreamSucceed(Seq(event), expVer)

    def appendToStreamFailed(event: EventData, expVer: ExpectedVersion = Any) = {
      actor ! AppendToStream(streamId, expVer, Seq(event))
      expectMsgType[AppendToStreamFailed].reason
    }
  }
}
