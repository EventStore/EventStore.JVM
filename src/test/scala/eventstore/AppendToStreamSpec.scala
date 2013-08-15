package eventstore

import OperationFailed._
import akka.testkit.TestProbe

/**
 * @author Yaroslav Klymko
 */
class AppendToStreamSpec extends TestConnectionSpec {
  "append to stream" should {
    "succeed for zero events" in new AppendToStreamScope {
      actor ! appendToStream(NoStream)
      expectMsg(appendToStreamSucceed())
    }

    "create stream with NoStream exp ver on first write if does not exist" in new AppendToStreamScope {
      doAppendToStream(newEvent, NoStream)
      streamEvents must haveSize(1)
    }

    "create stream with ANY exp ver on first write if does not exist" in new AppendToStreamScope {
      doAppendToStream(newEvent, AnyVersion)
      streamEvents must haveSize(1)
    }

    "fail create stream with wrong exp ver if does not exist" in new AppendToStreamScope {
      failAppendToStream(newEvent, EmptyStream) mustEqual WrongExpectedVersion
      failAppendToStream(newEvent, Version(1)) mustEqual WrongExpectedVersion
    }

    "fail writing with correct exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, EmptyStream) mustEqual StreamDeleted
    }

    "fail writing with any exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, AnyVersion) mustEqual StreamDeleted
    }

    "fail writing with invalid exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, Version(1)) mustEqual StreamDeleted
    }

    "succeed writing with correct exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      doAppendToStream(newEvent, EmptyStream, 1)
      doAppendToStream(newEvent, Version(1), 2)
    }

    "succeed writing with any exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      doAppendToStream(newEvent, AnyVersion, 1)
      doAppendToStream(newEvent, AnyVersion, 2)
    }

    "fail writing with wrong exp ver to existing stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      failAppendToStream(newEvent, NoStream) mustEqual WrongExpectedVersion
      failAppendToStream(newEvent, Version(1)) mustEqual WrongExpectedVersion
    }

    "be able to append multiple events at once" in new AppendToStreamScope {
      val events = appendMany()
      streamEvents mustEqual events
    }

    "be able to append many events at once" in new AppendToStreamScope {
      val size = 1000
      appendMany(size = size)
      actor ! ReadStreamEvents(streamId, -1, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsSucceed].events.head.eventRecord.number mustEqual EventNumber.Exact(size - 1)
      deleteStream()
    }

    "be able to append many events at once concurrently" in new AppendToStreamScope {
      val n = 10
      val size = 100

      Seq.fill(n)(TestProbe()).foreach(x => appendMany(size = size, testKit = x))

      actor ! ReadStreamEvents(streamId, -1, 1, ReadDirection.Backward)
      expectMsgType[ReadStreamEventsSucceed].events.head.eventRecord.number mustEqual EventNumber.Exact(size * n - 1)

      deleteStream()
    }
  }

  trait AppendToStreamScope extends TestConnectionScope {
    def failAppendToStream(event: Event, expVer: ExpectedVersion = AnyVersion) = {
      actor ! appendToStream(expVer, event)
      expectMsgPF() {
        case AppendToStreamFailed(reason, _) => reason
      }
    }
  }
}
