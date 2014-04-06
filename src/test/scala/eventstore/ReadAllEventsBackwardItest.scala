package eventstore

class ReadAllEventsBackwardITest extends TestConnection {
  sequential

  implicit val direction = ReadDirection.Backward
  val startPosition = Position.Last

  "read all events backward" should {
    "fail if count <= 0" in new TestConnectionScope {
      // TODO server does not validate maxCount, WHY?
      readAllEventsCompleted(Position.Last, 0) must throwAn[IllegalArgumentException]
      readAllEventsCompleted(Position.Last, -1) must throwAn[IllegalArgumentException]
    }

    "fail if count > MaxBatchSize" in new TestConnectionScope {
      readAllEventsCompleted(Position.Last, MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "return empty slice if asked to read from start" in new TestConnectionScope {
      readAllEvents(Position.First, 1) must beEmpty
    }

    "return partial slice if not enough events" in new TestConnectionScope {
      appendMany()
      val position = allStreamsEvents()(ReadDirection.Forward).take(5).last.position
      readAllEvents(position, 10).size must beLessThan(5)
    }

    "return events in reversed order compared to written" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(startPosition, 10).map(_.data) mustEqual events.reverse
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
      readAllEvents(Position.Last, 1).head must beLike {
        case Event.StreamDeleted(`streamId`, _) => ok
      }
    }

    "read events from deleted streams" in new TestConnectionScope {
      val event = appendEventToCreateStream()
      deleteStream()
      val events = readAllEvents(startPosition, 10).filter(_.streamId == streamId)
      events must haveSize(2)
      events.last.data mustEqual event
      events.head must beLike {
        case Event.StreamDeleted(`streamId`, _) => ok
      }
    }

    "read not modified events" in new TestConnectionScope {
      def read() = readAllEventsCompleted(startPosition, 10)

      val r1 = read()
      val r2 = read()
      r1.events mustEqual r2.events
    }

    "fail to read from wrong position" in new TestConnectionScope {
      val position = readAllEventsCompleted(startPosition, 10).nextPosition
      val wrongPosition = Position(
        commitPosition = position.commitPosition - 1,
        preparePosition = position.preparePosition - 1)
      val failed = readAllEventsFailed(wrongPosition, 10)
      failed mustEqual EsError.Error
    }

    "not read linked events if resolveLinkTos = false" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val event = readAllEventsCompleted(Position.Last, 1, resolveLinkTos = false).events.head.event
      event mustEqual link
    }

    "read linked events if resolveLinkTos = true" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val event = readAllEventsCompleted(Position.Last, 1, resolveLinkTos = true).events.head.event
      event mustEqual ResolvedEvent(linked, link)
    }
  }
}
