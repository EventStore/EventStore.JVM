package eventstore

import OperationResult._

import ReadDirection.{Forward, Backward}

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsForwardSpec extends TestConnectionSpec {
  "read stream events forward" should {
    "fail if count <= 0" in todo

    "fail if start < 0 " in todo

    "fail if stream not found" in new ReadStreamForwardScope {
      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Forward)

      // ReadStreamEventsCompleted(List(),NoStream,-1,-1,true,432043,Forward) // TODO simplify

      expectMsgPF() {
        case x@ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, _, _, _, _, Forward) =>
          println(x)
          true
      }


    }

    "fail if stream has been deleted" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(EmptyStream)
      expectMsg(deleteStreamCompleted)

      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Forward)


      // ReadStreamEventsCompleted(List(),StreamDeleted,-1,-1,true,432644,Forward) // TODO simplify
      expectMsgPF() {
        case x@ReadStreamEventsCompleted(Nil, ReadStreamResult.StreamDeleted, _, _, _, _, Forward) =>
          println(x)
          true
      }
    }

    "get single event if stream is empty" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Forward)
      // ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamForwardSpec-b5ba01e8-90fb-4847-a86b-8c3e372e870f,0,ByteString(15, 74, 115, 94, -45, 124, -85, 48, 81, -77, 28, 13, 7, 25, -47, -101),Some($stream-created),ByteString(),Some(ByteString(82, 101, 97, 100, 83, 116, 114, 101, 97, 109, 70, 111, 114, 119, 97, 114, 100, 83, 112, 101, 99))),None)),Success,1,0,true,433833,Forward)

      expectMsgPF() {
        case ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(`streamId`, 0, _, "$stream-created", ByteString.empty, Some(_)), None)), ReadStreamResult.Success, 1, 0, true, _, Forward) => true
      }

    }

    "get empty slice if stream is empty and start from 1" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! ReadStreamEvents(streamId, 1, 1000, resolveLinkTos = false, Forward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.Success, 1, 0, true, _, Forward) => true
      }
    }

    "get empty slice if called with non existing range" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 10, 1000, resolveLinkTos = false, Forward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.Success, 3, 2, true, _, Forward) => true
      }
    }

    "get partial slice if not enough events in stream" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Forward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, 3, 2, true, _, Forward) => events
      } must haveSize(3)
    }

    "get partial slice if not enough events in stream and called with Int.Max count" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 0, Int.MaxValue, resolveLinkTos = false, Forward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, 3, 2, true, _, Forward) => events
      } must haveSize(3)
    }

    "get events in same order as written" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (0 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 1, Int.MaxValue, resolveLinkTos = false, Forward)

      val resolvedEvents = events.zipWithIndex.map {
        case (x, idx) => ResolvedIndexedEvent(eventRecord(idx + 1, x), None)
      }


      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`resolvedEvents`, ReadStreamResult.Success, _, _, true, _, Forward) =>
          println(x)
          true
      }
    }

    "be able to read single event from arbitrary position" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (0 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      val event = ResolvedIndexedEvent(eventRecord(5, events(4)), None)

      actor ! ReadStreamEvents(streamId, 5, 1, resolveLinkTos = false, Forward)

      //      ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamForwardSpec-afb47482-a048-4182-b2f2-fb4dc522ab7a,5,ByteString(93, 70, 80, 60, 122, -110, -30, -57, -106, 44, -75, -94, -48, -7, -69, -83),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None)),Success,6,11,false,492132,Forward)

      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`event` :: Nil, ReadStreamResult.Success, 6, 11, false, _, Forward) =>

          //          events.zipWithIndex.collectFirst{
          //            case (lll,idx) if z.head.event.eventId == lll.eventId => println(idx)
          //          }
          println(x.events.head)
          true
      }
    }

    "be able to read slice from arbitrary position" in new ReadStreamForwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (0 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      val event1 = ResolvedIndexedEvent(eventRecord(5, events(4)), None)
      val event2 = ResolvedIndexedEvent(eventRecord(6, events(5)), None)
      val event3 = ResolvedIndexedEvent(eventRecord(7, events(6)), None)

      actor ! ReadStreamEvents(streamId, 5, 3, resolveLinkTos = false, Forward)

      //      ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamForwardSpec-afb47482-a048-4182-b2f2-fb4dc522ab7a,5,ByteString(93, 70, 80, 60, 122, -110, -30, -57, -106, 44, -75, -94, -48, -7, -69, -83),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None)),Success,6,11,false,492132,Forward)

      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`event1` :: `event2` :: `event3` :: Nil, ReadStreamResult.Success, 8, 11, false, _, Forward) =>
          println(x.events.head)
          true
      }
    }

  }


  trait ReadStreamForwardScope extends TestConnectionScope

}
