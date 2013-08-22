package eventstore

/**
 * @author Yaroslav Klymko
 */
class ReadAllEventsBackwardSpec extends TestConnectionSpec {
  sequential

  implicit val direction = ReadDirection.Backward
  val startPosition = Position.Last

  "read all events backward" should {
    "fail if count <= 0" in new TestConnectionScope {
      // TODO server does not validate maxCount, WHY?
      readAllEventsSucceed(Position.Last, 0) must throwAn[IllegalArgumentException]
      readAllEventsSucceed(Position.Last, -1) must throwAn[IllegalArgumentException]
    }

    "fail if count > MacBatchSize" in new TestConnectionScope {
      readAllEventsSucceed(Position.Last, MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "return empty slice if asked to read from start" in new TestConnectionScope {
      readAllEvents(Position.First, 1) must beEmpty
    }

    "return partial slice if not enough events" in new TestConnectionScope {
      appendMany()
      val position = allStreamsResolvedEvents()(ReadDirection.Forward).take(5).last.position
      readAllEvents(position, 10).size must beLessThan(5)
    }

    "return events in reversed order compared to written" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(startPosition, 10) mustEqual events.reverse
    }

    "be able to read all one by one until end of stream" in new TestConnectionScope {
      allStreamsEvents(1).force
    }

    "be able to read all slice by slice" in new TestConnectionScope {
      allStreamsEvents(5).force
    }

    "read 'streamDeleted' events" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      readAllEventRecords(Position.Last, 1).head must beLike {
        case EventRecord.StreamDeleted(`streamId`, EventNumber.Exact(1), _) => ok
      }
    }

    "read events from deleted streams" in new TestConnectionScope {
      val event = appendEventToCreateStream()
      deleteStream()
      val events = readAllEventRecords(startPosition, 10).filter(_.streamId == streamId)
      events must haveSize(2)
      println(events)
      events.last.event mustEqual event
      events.head must beLike {
        case EventRecord.StreamDeleted(`streamId`, EventNumber.Exact(1), _) => ok
      }
    }

    "read not modified events" in new TestConnectionScope {
      def read() = readAllEventsSucceed(startPosition, 10)

      val r1 = read()
      val r2 = read()
      r1.resolvedEvents mustEqual r2.resolvedEvents
    }

    "fail to read from wrong position" in new TestConnectionScope {
      val position = readAllEventsSucceed(startPosition, 10).nextPosition
      val wrongPosition = Position(
        commitPosition = position.commitPosition - 1,
        preparePosition = position.preparePosition - 1)
      val failed = readAllEventsFailed(wrongPosition, 10)
      failed.reason mustEqual ReadAllEventsFailed.Error
      failed.message must beSome
    }

    "not read linked events if resolveLinkTos = false" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val resolvedIndexedEvent = readAllEventsSucceed(Position.Last, 1, resolveLinkTos = false).resolvedEvents.head
      resolvedIndexedEvent.eventRecord mustEqual link
      resolvedIndexedEvent.link must beNone
    }

    "read linked events if resolveLinkTos = true" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val resolvedIndexedEvent = readAllEventsSucceed(Position.Last, 1, resolveLinkTos = true).resolvedEvents.head
      resolvedIndexedEvent.eventRecord mustEqual linked
      resolvedIndexedEvent.link must beSome(link)
    }
  }
}
