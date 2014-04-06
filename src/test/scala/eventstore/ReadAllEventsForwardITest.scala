package eventstore

class ReadAllEventsForwardITest extends TestConnection {
  sequential

  implicit val direction = ReadDirection.Forward
  val startPosition = Position.First

  "read all events forward" should {
    "fail if count <= 0" in new TestConnectionScope {
      // TODO server does not validate maxCount, WHY?
      readAllEventsCompleted(Position.First, 0) must throwAn[IllegalArgumentException]
      readAllEventsCompleted(Position.First, -1) must throwAn[IllegalArgumentException]
    }

    "fail if count > MaxBatchSize" in new TestConnectionScope {
      readAllEventsCompleted(Position.First, MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "return empty slice if asked to read from end" in new TestConnectionScope {
      val events = appendMany()
      readAllEvents(Position.Last, 1) must beEmpty
    }

    "return partial slice if not enough events" in new TestConnectionScope {
      val events = appendMany()
      val size = events.size
      val stream = allStreamsEvents()(ReadDirection.Backward)
      val position = stream(size / 2).position
      readAllEvents(position, size).size must beLessThan(size)
    }

    "return events in same order as written" in new TestConnectionScope {
      val events = appendMany()
      allStreamsEvents().map(_.event.data).takeRight(events.length).toSeq mustEqual events
    }

    "be able to read all one by one until end of stream" in new TestConnectionScope {
      allStreamsEvents(1).force
    }

    "be able to read all slice by slice" in new TestConnectionScope {
      allStreamsEvents().force
    }

    "read 'streamDeleted' events" in new TestConnectionScope {
      appendEventToCreateStream()
      deleteStream()
      val position = allStreamsEvents()(ReadDirection.Backward).take(5).last.position

      readAllEvents(position, 10).last must beLike {
        case Event.StreamDeleted(`streamId`, _) => ok
      }
    }

    "read events from deleted streams" in new TestConnectionScope {
      val event = appendEventToCreateStream()
      deleteStream()
      val position = allStreamsEvents()(ReadDirection.Backward).take(5).last.position
      val events = readAllEvents(position, 10).filter(_.streamId == streamId)
      events must haveSize(2)
      events.head.data mustEqual event
      events.last must beLike {
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
      val position = allStreamsEvents()(ReadDirection.Backward).head.position
      val event = readAllEventsCompleted(position, 1, resolveLinkTos = false).events.last.event
      event mustEqual link
    }

    "read linked events if resolveLinkTos = true" in new TestConnectionScope {
      val (linked, link) = linkedAndLink()
      val position = allStreamsEvents()(ReadDirection.Backward).head.position
      val event = readAllEventsCompleted(position, 1, resolveLinkTos = true).events.last.event
      event mustEqual ResolvedEvent(linked, link)
    }
  }
}