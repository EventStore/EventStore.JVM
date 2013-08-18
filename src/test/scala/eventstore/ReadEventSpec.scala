package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadEventSpec extends TestConnectionSpec {

  "read event" should {
    "fail if stream not found" in new ReadEventScope {
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.NoStream
    }

    "fail if stream deleted" in new ReadEventScope {
      appendEventToCreateStream()
      deleteStream()
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.StreamDeleted
    }

    "fail if stream does not have such event" in new ReadEventScope {
      appendEventToCreateStream()
      readEventFailed(EventNumber(5)) mustEqual ReadEventFailed.NotFound
    }

    "return existing event" in new ReadEventScope {
      appendEventToCreateStream()
      readEvent(EventNumber.First)
    }

    "return last event in stream if event number is minus one" in new ReadEventScope {
      val events = appendMany()
      readEvent(EventNumber.Last) mustEqual events.last
    }

    "return linked event if resolveLinkTos = false" in new ReadEventScope {
      append(newEvent)
      val origin = readEventSucceed(EventNumber.First).eventRecord

      val link = origin.link(newUuid)
      append(link)

      when(resolveLinkTos = false) {
        resolvedIndexedEvent =>
          resolvedIndexedEvent.eventRecord.event mustEqual link
          resolvedIndexedEvent.link must beNone
      }

      when(resolveLinkTos = true) {
        resolvedIndexedEvent =>
          resolvedIndexedEvent.eventRecord mustEqual origin
          resolvedIndexedEvent.link.map(_.event) must beSome(link)
      }
    }
  }


  trait ReadEventScope extends TestConnectionScope {
    def readEventFailed(eventNumber: EventNumber) = {
      actor ! ReadEvent(streamId, eventNumber)
      expectMsgType[ReadEventFailed].reason
    }

    def when(resolveLinkTos: Boolean)(f: ResolvedIndexedEvent => Any) {
      f(readEventSucceed(EventNumber.Last, resolveLinkTos = resolveLinkTos))
    }

    def readEventSucceed(eventNumber: EventNumber, resolveLinkTos: Boolean = false) = {
      actor ! ReadEvent(streamId, eventNumber, resolveLinkTos = resolveLinkTos)
      val resolvedIndexedEvent = expectMsgType[ReadEventSucceed].resolvedIndexedEvent
      if (!resolveLinkTos) resolvedIndexedEvent.link must beEmpty
      val eventRecord = resolvedIndexedEvent.eventRecord
      eventRecord.streamId mustEqual streamId
      if (eventNumber != EventNumber.Last) eventRecord.number mustEqual eventNumber
      resolvedIndexedEvent
    }

    def readEvent(eventNumber: EventNumber): Event = readEventSucceed(eventNumber).eventRecord.event
  }
}