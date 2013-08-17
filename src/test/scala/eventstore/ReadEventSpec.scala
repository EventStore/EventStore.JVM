package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadEventSpec extends TestConnectionSpec {

  "read event" should {
    "fail if stream not found" in new ReadEventScope {
      failReadEvent(EventNumber(5)) mustEqual ReadEventFailed.NoStream
    }

    "fail if stream deleted" in new ReadEventScope {
      appendEventToCreateStream()
      deleteStream()
      failReadEvent(EventNumber(5)) mustEqual ReadEventFailed.StreamDeleted
    }

    "fail if stream does not have such event" in new ReadEventScope {
      appendEventToCreateStream()
      failReadEvent(EventNumber(5)) mustEqual ReadEventFailed.NotFound
    }

    "return existing event" in new ReadEventScope {
      appendEventToCreateStream()
      readEvent(EventNumber.First)
    }

    "return last event in stream if event number is minus one" in new ReadEventScope {
      val events = appendMany()
      readEvent(EventNumber.Last) mustEqual events.last
    }
  }


  trait ReadEventScope extends TestConnectionScope {
    def failReadEvent(eventNumber: EventNumber) = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = false)
      expectMsgPF() {
        case ReadEventFailed(reason, _) => reason
      }
    }

    def readEvent(eventNumber: EventNumber): Event = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = false)
      expectMsgPF() {
        case ReadEventSucceed(ResolvedIndexedEvent(EventRecord(`streamId`, `eventNumber`, event), None)) => event
        case ReadEventSucceed(ResolvedIndexedEvent(EventRecord(`streamId`, _, event), None)) if eventNumber == EventNumber.Last => event
      }
    }
  }
}