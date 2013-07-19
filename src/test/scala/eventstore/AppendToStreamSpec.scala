package eventstore

import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class AppendToStreamSpec extends TestConnectionSpec {
  "append to stream" should {
    "succeed for zero events" in new AppendToStreamScope {
      actor ! appendToStream(NoStream)
      expectMsg(appendToStreamCompleted())
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
      failAppendToStream(newEvent, EmptyStream, WrongExpectedVersion)
      failAppendToStream(newEvent, Version(1), WrongExpectedVersion)
    }

    "fail writing with correct exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, EmptyStream, StreamDeleted)
    }

    "fail writing with any exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, AnyVersion, StreamDeleted)
    }

    "fail writing with invalid exp ver to deleted stream" in new AppendToStreamScope {
      appendEventToCreateStream()
      deleteStream()
      failAppendToStream(newEvent, Version(1), StreamDeleted)
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
      failAppendToStream(newEvent, NoStream, WrongExpectedVersion)
      failAppendToStream(newEvent, Version(1), WrongExpectedVersion)
    }

    "be able to append multiple events at once" in new AppendToStreamScope {
      val events = (0 until 100).map(_ => newEvent)
      appendMany(events)
      streamEvents mustEqual events
    }
  }

  trait AppendToStreamScope extends TestConnectionScope {
    def failAppendToStream(event: Event, expVer: ExpectedVersion = AnyVersion, result: Value) {
      actor ! appendToStream(expVer, event)
      expectMsgPF() {
        case AppendToStreamCompleted(`result`, Some(_), _) => true
      }
    }
  }
}
