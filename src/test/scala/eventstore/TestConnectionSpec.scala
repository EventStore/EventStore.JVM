package eventstore

import akka.testkit._
import akka.actor.ActorSystem
import tcp.ConnectionActor
import org.specs2.mutable.{SpecificationWithJUnit, After}
import org.specs2.time.NoDurationConversions
import scala.concurrent.duration._
import ReadDirection._


/**
 * @author Yaroslav Klymko
 */
abstract class TestConnectionSpec extends SpecificationWithJUnit with NoDurationConversions {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = newStreamId

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(Settings()))

    def deleteStream(expVer: ExpectedVersion.Existing = ExpectedVersion.Any) {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, expVer))(probe.ref)
      probe.expectMsg(DeleteStreamSucceed)
    }

    def newEvent = Event(newUuid, "test",
      data = ByteString( """{"data":"data"}"""),
      metadata = ByteString( """{"metadata":"metadata"}"""))

    def appendToStreamSucceed(events: Seq[Event],
                              expectedVersion: ExpectedVersion = ExpectedVersion.Any,
                              streamId: EventStream.Id = streamId,
                              testKit: TestKitBase = this): EventNumber.Exact = {
      actor.!(AppendToStream(streamId, expectedVersion, events))(testKit.testActor)
      val firstEventNumber = testKit.expectMsgType[AppendToStreamSucceed].firstEventNumber
      if (expectedVersion == ExpectedVersion.NoStream) firstEventNumber mustEqual EventNumber.First
      firstEventNumber
    }

    def appendEventToCreateStream(): Event = {
      val event = newEvent.copy(eventType = "first event")
      appendToStreamSucceed(Seq(event), ExpectedVersion.NoStream, testKit = TestProbe()) mustEqual EventNumber.First
      event
    }

    def append(x: Event): EventRecord = EventRecord(streamId, appendToStreamSucceed(Seq(x), testKit = TestProbe()), x)

    def linkedAndLink(): (EventRecord, EventRecord) = {
      val linked = append(newEvent.copy(eventType = "linked"))
      append(newEvent)
      val link = append(linked.link(newUuid))
      (linked, link)
    }

    def appendMany(size: Int = 10, testKit: TestKitBase = this): Seq[Event] = {
      val duration = FiniteDuration(10, SECONDS)
      val events = (1 to size).map(_ => newEvent)

      def loop(n: Int) {
        actor.!(AppendToStream(streamId, ExpectedVersion.Any, events))(testKit.testActor)
        testKit.expectMsgPF(duration) {
          case AppendToStreamSucceed(_) => true
          case AppendToStreamFailed(OperationFailed.PrepareTimeout, _) if n < 3 => loop(n + 1) // TODO
        }
      }

      loop(0)
      events
    }


    def readStreamEventsSucceed(fromEventNumber: EventNumber, maxCount: Int, resolveLinkTos: Boolean = false)
                               (implicit direction: ReadDirection.Value) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadStreamEventsSucceed]
      result.direction mustEqual direction

      val resolvedIndexedEvents = result.resolvedIndexedEvents
      resolvedIndexedEvents.size must beLessThanOrEqualTo(maxCount)
      mustBeSorted(resolvedIndexedEvents)
      resolvedIndexedEvents.foreach {
        x =>
          if (!resolveLinkTos) x.link must beEmpty

          def verify(x: EventRecord) = {
            x.streamId mustEqual streamId
            val number = x.number
            number must beLessThanOrEqualTo(result.lastEventNumber)

            direction match {
              case Forward => x.number must beGreaterThanOrEqualTo(fromEventNumber)
              case Backward => x.number must beLessThanOrEqualTo(fromEventNumber)
            }
          }
          verify(x.link getOrElse x.eventRecord)
      }
      result
    }

    def readStreamEvents(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      val result = readStreamEventsSucceed(fromEventNumber = fromEventNumber, maxCount = maxCount)
      result.resolvedIndexedEvents.map(_.eventRecord.event)
    }

    def readStreamEventsFailed(fromEventNumber: EventNumber, maxCount: Int)
                              (implicit direction: ReadDirection.Value): ReadStreamEventsFailed = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val failed = expectMsgType[ReadStreamEventsFailed]
      failed.direction mustEqual direction
      failed
    }

    def readStreamEventsFailed(implicit direction: ReadDirection.Value): ReadStreamEventsFailed =
      readStreamEventsFailed(EventNumber.start(direction), 500)

    def streamEvents(implicit direction: ReadDirection.Value = Forward): Stream[Event] = {
      def loop(position: EventNumber): Stream[ResolvedIndexedEvent] = {
        val result = readStreamEventsSucceed(position, 500)
        val resolvedIndexedEvents = result.resolvedIndexedEvents
        if (resolvedIndexedEvents.isEmpty || result.endOfStream) resolvedIndexedEvents.toStream
        else resolvedIndexedEvents.toStream #::: loop(result.nextEventNumber)
      }
      loop(EventNumber.start(direction)).map(_.eventRecord.event)
    }


    def expectEventAppeared(testKit: TestKitBase = this) = {
      val resolvedEvent = testKit.expectMsgType[StreamEventAppeared].resolvedEvent
      resolvedEvent.eventRecord.streamId mustEqual streamId
      resolvedEvent
    }

    def mustBeSorted[T](xs: Seq[T])(implicit direction: ReadDirection.Value, ordering: Ordering[T]) {
      // TODO
      xs.map {
        case ResolvedIndexedEvent(linked, Some(link)) => ResolvedIndexedEvent(link, None).asInstanceOf[T]
        case x => x
      } must beSorted(direction match {
        case Forward => ordering
        case Backward => ordering.reverse
      })
    }

    def readAllEventsSucceed(position: Position, maxCount: Int, resolveLinkTos: Boolean = false)
                            (implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction, resolveLinkTos = resolveLinkTos)
      val result = expectMsgType[ReadAllEventsSucceed](FiniteDuration(10, SECONDS))
      result.direction mustEqual direction

      val resolvedEvents = result.resolvedEvents
      resolvedEvents.size must beLessThanOrEqualTo(maxCount)
      resolvedEvents.foreach {
        x =>
          if (!resolveLinkTos) x.link must beEmpty
          direction match {
            case Forward => x.position must beGreaterThanOrEqualTo(position)
            case Backward => x.position must beLessThanOrEqualTo(position)
          }
      }
      mustBeSorted(resolvedEvents)

      result
    }

    def readAllEventsFailed(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction)
      val failed = expectMsgType[ReadAllEventsFailed]
      failed.direction mustEqual direction
      failed.position mustEqual position
      failed
    }

    def readAllEventRecords(position: Position, maxCount: Int)
                           (implicit direction: ReadDirection.Value): Seq[EventRecord] =
      readAllEventsSucceed(position = position, maxCount = maxCount).resolvedEvents.map(_.eventRecord)

    // TODO
    def readAllEvents(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value): Seq[Event] =
      readAllEventRecords(position, maxCount).map(_.event)

    def allStreamsResolvedEvents(maxCount: Int = 500)(implicit direction: ReadDirection.Value): Stream[ResolvedEvent] = {
      def loop(position: Position): Stream[ResolvedEvent] = {
        val result = readAllEventsSucceed(position, maxCount)
        val resolvedEvents = result.resolvedEvents
        if (resolvedEvents.isEmpty) Stream()
        else resolvedEvents.toStream #::: loop(result.nextPosition)
      }
      loop(Position.start(direction))
    }

    def allStreamsEvents(maxCount: Int = 500)(implicit direction: ReadDirection.Value) =
      allStreamsResolvedEvents(maxCount).map(_.eventRecord.event)

    def newStreamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    def after = {
      /*
      try actor ! DeleteStream(streamId, AnyVersion, requireMaster = true)
      catch {
        case e: Throwable =>
      }*/

      // TODO
    }
  }

}

