package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadEventITest extends TestConnection {

  "read event" should {
    "fail if stream not found" in new ReadEventScope {
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.Reason.NoStream
    }

    "fail if stream deleted" in new ReadEventScope {
      appendEventToCreateStream()
      deleteStream()
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.Reason.StreamDeleted
    }

    "fail if stream does not have such event" in new ReadEventScope {
      appendEventToCreateStream()
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.Reason.NotFound
    }

    "return existing event" in new ReadEventScope {
      appendEventToCreateStream()
      readEventData(EventNumber.First)
    }

    "return last event in stream if event number is minus one" in new ReadEventScope {
      val events = appendMany()
      readEventData(EventNumber.Last) mustEqual events.last
    }

    "return link event if resolveLinkTos = false" in new ReadEventScope {
      val (linked, link) = linkedAndLink()
      val event = readEventSucceed(EventNumber.Last, resolveLinkTos = false)
      event mustEqual link
    }

    "return linked event if resolveLinkTos = true" in new ReadEventScope {
      val (linked, link) = linkedAndLink()
      val event = readEventSucceed(EventNumber.Last, resolveLinkTos = true)
      event mustEqual ResolvedEvent(linked, link)
    }
  }

  trait ReadEventScope extends TestConnectionScope {
    def readEventFailed(eventNumber: EventNumber) = {
      actor ! ReadEvent(streamId, eventNumber)
      expectMsgType[ReadEventFailed].reason
    }

    def readEventData(eventNumber: EventNumber): EventData = readEventSucceed(eventNumber).data
  }
}