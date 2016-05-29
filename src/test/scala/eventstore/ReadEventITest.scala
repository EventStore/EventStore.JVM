package eventstore

class ReadEventITest extends TestConnection {

  "read event" should {
    "fail if stream not found" in new ReadEventScope {
      readEventFailed(EventNumber(5)) must throwA[StreamNotFoundException]
    }

    "fail if stream deleted" in new ReadEventScope {
      appendEventToCreateStream()
      deleteStream()
      readEventFailed(EventNumber(5)) must throwA[StreamDeletedException]
    }

    "fail if stream does not have such event" in new ReadEventScope {
      appendEventToCreateStream()
      readEventFailed(EventNumber(5)) must throwA[EventNotFoundException]
    }

    "return existing event" in new ReadEventScope {
      val data = appendEventToCreateStream()
      readEventData(EventNumber.First) mustEqual data
    }

    "return last event in stream if event number is minus one" in new ReadEventScope {
      val events = appendMany()
      readEventData(EventNumber.Last) mustEqual events.last
    }

    "return link event if resolveLinkTos = false" in new ReadEventScope {
      val (linked, link) = linkedAndLink()
      val event = readEventCompleted(EventNumber.Last, resolveLinkTos = false)
      event mustEqual link
    }

    "return linked event if resolveLinkTos = true" in new ReadEventScope {
      val (linked, link) = linkedAndLink()
      val event = readEventCompleted(EventNumber.Last, resolveLinkTos = true)
      event mustEqual ResolvedEvent(linked, link)
    }

    "return link event if resolveLinkTos = false and linked stream is deleted" in new ReadEventScope {
      val linkedData = newEventData.copy(eventType = "linked")
      val linkedStreamId = newStreamId
      val linked = EventRecord(linkedStreamId, writeEventsCompleted(List(linkedData), streamId = linkedStreamId).get.start, linkedData, Some(date))
      val link = append(linked.link())

      actor ! DeleteStream(linkedStreamId, hard = true)
      expectMsgType[DeleteStreamCompleted]

      readEventCompleted(link.number) mustEqual link
    }

    "return link with undefined streamId event if resolveLinkTos = true and linked stream is deleted" in new ReadEventScope {
      val linkedData = newEventData.copy(eventType = "linked")
      val linkedStreamId = newStreamId
      val linked = EventRecord(linkedStreamId, writeEventsCompleted(List(linkedData), streamId = linkedStreamId).get.start, linkedData, Some(date))
      val link = append(linked.link())

      actor ! DeleteStream(linkedStreamId, hard = true)
      expectMsgType[DeleteStreamCompleted]

      actor ! ReadEvent(streamId, link.number, resolveLinkTos = true)
      expectMsgType[ReadEventCompleted].fixDate.event mustEqual ResolvedEvent(EventRecord.Deleted, link).fixDate
    }
  }

  private trait ReadEventScope extends TestConnectionScope {
    def readEventFailed(eventNumber: EventNumber) = {
      actor ! ReadEvent(streamId, eventNumber)
      expectEsException()
    }

    def readEventData(eventNumber: EventNumber): EventData = readEventCompleted(eventNumber).data
  }
}