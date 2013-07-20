package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadEventSpec extends TransactionSpec {

  "read event" should {

    "throw exception if stream id is null" in todo
    "throw exception if stream id is empty" in todo
    "throw exception if event number is less then -1" in todo

    "fail if stream not found" in new ReadEventScope {
      failReadEvent(5, ReadEventFailed.NoStream)
    }

    "fail if stream deleted" in new ReadEventScope {
      appendEventToCreateStream()
      deleteStream()
      failReadEvent(5, ReadEventFailed.StreamDeleted)
    }

    "fail if stream does not have such event" in new ReadEventScope {
      appendEventToCreateStream()
      failReadEvent(5, ReadEventFailed.NotFound)
    }

    "return existing event" in new ReadEventScope {
      appendEventToCreateStream()
      readEvent(0)
    }

    "return last event in stream if event number is minus one" in new ReadEventScope {
      val events = appendMany()
      readEvent(-1) mustEqual events.last
    }
  }


  trait ReadEventScope extends TransactionScope {
    def failReadEvent(eventNumber: Int, reason: ReadEventFailed.Value) {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = false)
      expectMsg(ReadEventFailed(reason))
    }

    def readEvent(eventNumber: Int): Event = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = false)
      expectMsgPF() {
        case ReadEventSucceed(ResolvedIndexedEvent(EventRecord(`streamId`, `eventNumber`, event), None)) => event
        case ReadEventSucceed(ResolvedIndexedEvent(EventRecord(`streamId`, _, event), None)) if eventNumber == -1 => event
      }
    }
  }

}
