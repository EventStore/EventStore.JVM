package eventstore

import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class AppendToStreamSpec extends TestConnectionSpec {
  "append to stream" should {
    "fail for zero events" in new AppendToStreamScope {
      actor ! writeEvents(NoStream)
      expectMsg(BadRequest)
    }

    "create stream with NoStream exp ver on first write if does not exist" in new AppendToStreamScope {
      actor ! writeEvents(NoStream, newEvent)
      expectMsg(writeEventsCompleted())

      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, _, _, _, _, _) => events
      } must haveSize(2)
    }

    "create stream with ANY exp ver on first write if does not exist" in new AppendToStreamScope {
      actor ! writeEvents(AnyVersion, newEvent)
      expectMsg(writeEventsCompleted())

      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, _, _, _, _, _) => events
      } must haveSize(2)
    }

    "fail create stream with wrong exp ver if does not exist" in new AppendToStreamScope {
      actor ! writeEvents(EmptyStream, newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(WrongExpectedVersion, Some(_), _) => true
      }

      actor ! writeEvents(Version(1), newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(WrongExpectedVersion, Some(_), _) => true
      }
    }

    "fail writing with correct exp ver to deleted stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! WriteEvents(streamId, EmptyStream, List(newEvent), requireMaster = true)
      expectMsgPF() {
        case WriteEventsCompleted(StreamDeleted, Some(_), _) => true
      }
    }

    "fail writing with any exp ver to deleted stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(StreamDeleted, Some(_), _) => true
      }
    }

    "fail writing with invalid exp ver to deleted stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! writeEvents(Version(1), newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(StreamDeleted, Some(_), _) => true
      }
    }

    "succeed writing with correct exp ver to existing stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(EmptyStream, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! writeEvents(Version(1), newEvent)
      expectMsg(writeEventsCompleted(2))
    }

    "succeed writing with any exp ver to existing stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! writeEvents(AnyVersion, newEvent)
      expectMsg(writeEventsCompleted(2))
    }

    "fail writing with wrong exp ver to existing stream" in new AppendToStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(NoStream, newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(WrongExpectedVersion, Some(_), _) => true
      }

      actor ! writeEvents(Version(1), newEvent)
      expectMsgPF() {
        case WriteEventsCompleted(WrongExpectedVersion, Some(_), _) => true
      }
    }

    "be able to append multiple events at once" in new AppendToStreamScope {
      val events = (0 until 100).map(_ => newEvent)

      actor ! writeEvents(NoStream, events: _*)
      expectMsg(writeEventsCompleted())

      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(xs, ReadStreamResult.Success, _, _, _, _, _) => xs
      } must haveSize(events.size + 1)
    }
  }

  trait AppendToStreamScope extends TestConnectionScope
}
