package eventstore

import org.joda.time.DateTime
import org.specs2.mutable.Specification

class ResolvedEventSpec extends Specification {
  "ResolvedEvent" should {
    "fallback to resolved event rather to link itself" in {
      val event = EventRecord(EventStream.Id("streamId1"), EventNumber.Exact(1), EventData("test"), Some(DateTime.now))
      val link = EventRecord(EventStream.Id("streamId2"), EventNumber.Exact(2), event.link(), Some(DateTime.now().plusSeconds(5)))
      val resolvedEvent = ResolvedEvent(event, link)

      resolvedEvent.streamId mustEqual event.streamId
      resolvedEvent.number mustEqual event.number
      resolvedEvent.data mustEqual event.data
      resolvedEvent.created mustEqual event.created
      resolvedEvent.record mustEqual link
    }
  }
}