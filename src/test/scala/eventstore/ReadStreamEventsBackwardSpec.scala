package eventstore

import OperationResult._
import ReadDirection.Backward

/**
 * @author Yaroslav Klymko
 */
class ReadStreamEventsBackwardSpec extends TestConnectionSpec {
  "read stream events forward" should {
    "fail if count <= 0" in todo

    "fail if stream not found" in new ReadStreamBackwardScope {
      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Backward)

      // ReadStreamEventsCompleted(List(),NoStream,-1,-1,true,432043,Backward) // TODO simplify

      expectMsgPF() {
        case x@ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, _, _, _, _, Backward) =>
          println(x)
          true
      }
    }

    "fail if stream has been deleted" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(EmptyStream)
      expectMsg(deleteStreamCompleted)

      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Backward)


      // ReadStreamEventsCompleted(List(),StreamDeleted,-1,-1,true,432644,Backward) // TODO simplify
      expectMsgPF() {
        case x@ReadStreamEventsCompleted(Nil, ReadStreamResult.StreamDeleted, _, _, _, _, Backward) =>
          println(x)
          true
      }
    }

    "get single event if stream is empty" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, Backward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(`streamId`, 0, _, "$stream-created", ByteString.empty, Some(_)), None)), ReadStreamResult.Success, -1, 0, true, _, Backward) => true
      }

    }

    "get empty slice if called with non existing range" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 1000, 10, resolveLinkTos = false, Backward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.Success, 2, 2, false, _, Backward) => true
      }
    }

    "get partial slice if not enough events in stream" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! writeEvents(AnyVersion, newEvent, newEvent)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 1, 1000, resolveLinkTos = false, Backward)


      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, -1, 2, true, _, Backward) => events
      } must haveSize(2)
    }

    "get events in reversed order as written" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (1 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, -1, 10, resolveLinkTos = false, Backward)

      val resolvedEvents = events.zipWithIndex.map {
        case (x, idx) => ResolvedIndexedEvent(eventRecord(idx + 1, x), None)
      }.reverse


      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`resolvedEvents`, ReadStreamResult.Success, _, _, false, _, Backward) => true
        //          println(xs.map(_.event.eventNumber))
        //          xs
      }
    }

    "be able to read single event from arbitrary position" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (1 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      val event = ResolvedIndexedEvent(eventRecord(5, events(4)), None)

      actor ! ReadStreamEvents(streamId, 5, 1, resolveLinkTos = false, Backward)

      //      ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamBackwardSpec-afb47482-a048-4182-b2f2-fb4dc522ab7a,5,ByteString(93, 70, 80, 60, 122, -110, -30, -57, -106, 44, -75, -94, -48, -7, -69, -83),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None)),Success,6,11,false,492132,Backward)

      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`event` :: Nil, ReadStreamResult.Success, 4, 10, false, _, Backward) =>

          //          events.zipWithIndex.collectFirst{
          //            case (lll,idx) if z.head.event.eventId == lll.eventId => println(idx)
          //          }
          println(x.events.head)
          true
      }
    }

    "be able to read slice from arbitrary position" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (1 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      val event5 = ResolvedIndexedEvent(eventRecord(5, events(4)), None)
      val event4 = ResolvedIndexedEvent(eventRecord(4, events(3)), None)
      val event3 = ResolvedIndexedEvent(eventRecord(3, events(2)), None)

      actor ! ReadStreamEvents(streamId, 5, 3, resolveLinkTos = false, Backward)

      //      ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamBackwardSpec-afb47482-a048-4182-b2f2-fb4dc522ab7a,5,ByteString(93, 70, 80, 60, 122, -110, -30, -57, -106, 44, -75, -94, -48, -7, -69, -83),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None)),Success,6,11,false,492132,Backward)

      // ReadStreamEventsCompleted(List(ResolvedIndexedEvent(EventRecord(ReadStreamEventsBackwardSpec-3f21b602-cabd-4f52-b5df-46153c2cc470,3,ByteString(-118, 65, -22, -95, 39, -19, -124, 117, 58, 125, 122, -81, 49, 1, -44, -100),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None), ResolvedIndexedEvent(EventRecord(ReadStreamEventsBackwardSpec-3f21b602-cabd-4f52-b5df-46153c2cc470,2,ByteString(54, 65, -123, -120, -104, -66, 113, 88, 81, -22, -118, -19, 93, -45, 75, -73),Some(test),ByteString(),Some(ByteString(116, 101, 115, 116))),None)),Success,1,10,false,667685,Backward)
      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`event5` :: `event4` :: `event3` :: Nil, ReadStreamResult.Success, 2, 10, false, _, Backward) =>
          println(x)
          true
      }
    }

    "be able to read first event" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (1 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))



      actor ! ReadStreamEvents(streamId, 1, 1, resolveLinkTos = false, Backward)

      val first = ResolvedIndexedEvent(eventRecord(1, events.head), None)
      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`first` :: Nil, ReadStreamResult.Success, 0, 10, false, _, Backward) =>
          println(x.events.head)
          true
      }
    }



    "be able to read last event" in new ReadStreamBackwardScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      val events = (1 to 10).map(_ => newEvent).toList
      actor ! writeEvents(AnyVersion, events: _*)
      expectMsg(writeEventsCompleted(1))

      actor ! ReadStreamEvents(streamId, 10, 1, resolveLinkTos = false, Backward)

      val last = ResolvedIndexedEvent(eventRecord(10, events.last), None)
      expectMsgPF() {
        case x@ReadStreamEventsCompleted(`last` :: Nil, ReadStreamResult.Success, 9, 10, false, _, Backward) =>
          println(x.events.head)
          true
      }
    }

  }

  trait ReadStreamBackwardScope extends TestConnectionScope

}
