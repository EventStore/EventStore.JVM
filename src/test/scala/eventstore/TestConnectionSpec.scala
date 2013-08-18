package eventstore

import akka.testkit._
import akka.actor.ActorSystem
import tcp.ConnectionActor
import org.specs2.mutable.{SpecificationWithJUnit, After}
import org.specs2.time.NoDurationConversions
import scala.concurrent.duration._
import scala.util.Random
import ReadDirection._


/**
 * @author Yaroslav Klymko
 */
abstract class TestConnectionSpec extends SpecificationWithJUnit with NoDurationConversions {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

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
                              testKit: TestKitBase = this): EventNumber.Exact = {
      actor.!(AppendToStream(streamId, expectedVersion, events))(testKit.testActor)
      val firstEventNumber = testKit.expectMsgType[AppendToStreamSucceed].firstEventNumber
      if (expectedVersion == ExpectedVersion.NoStream) firstEventNumber mustEqual EventNumber.First
      firstEventNumber
    }

    def appendEventToCreateStream() {
      appendToStreamSucceed(Seq(Event(newUuid, "first event")), ExpectedVersion.NoStream, TestProbe()) mustEqual EventNumber.First
    }

    def append(events: Event*) = appendToStreamSucceed(events)

    def appendMany(size: Int = 10, testKit: TestKitBase = this): Seq[Event] = {
      val duration = FiniteDuration(10, SECONDS)
      val events = (1 to size).map(_ => newEvent)

      def loop(n: Int) {
        actor.!(AppendToStream(streamId, ExpectedVersion.Any, events))(testKit.testActor)
        testKit.expectMsgPF(duration) {
          case AppendToStreamSucceed(_) => true
          case AppendToStreamFailed(OperationFailed.PrepareTimeout, _) if n < 3 => loop(n + 1)
        }
      }

      loop(0)
      events
    }


    def readStreamEventsSucceed(fromEventNumber: EventNumber, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val result = expectMsgType[ReadStreamEventsSucceed]
      result.direction mustEqual direction

      val resolvedIndexedEvents = result.resolvedIndexedEvents
      resolvedIndexedEvents.size must beLessThanOrEqualTo(maxCount)
      mustBeSorted(resolvedIndexedEvents)
      resolvedIndexedEvents.foreach {
        x =>
          x.link must beEmpty

          val number = x.eventRecord.number
          number must beLessThanOrEqualTo(result.lastEventNumber)

          direction match {
            case Forward => number must beGreaterThanOrEqualTo(fromEventNumber)
            case Backward => number must beLessThanOrEqualTo(fromEventNumber)
          }
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

    def readStreamEventsFailed(implicit direction: ReadDirection.Value): ReadStreamEventsFailed = {
      val position = direction match {
        case Forward => EventNumber.First
        case Backward => EventNumber.Last
      }
      readStreamEventsFailed(position, 500)
    }


    def streamEvents: Stream[Event] = {
      implicit val direction = Forward
      def loop(position: EventNumber): Stream[ResolvedIndexedEvent] = {
        val result = readStreamEventsSucceed(position, 500)
        val resolvedIndexedEvents = result.resolvedIndexedEvents
        if (resolvedIndexedEvents.isEmpty || result.endOfStream) resolvedIndexedEvents.toStream
        else resolvedIndexedEvents.toStream #::: loop(result.nextEventNumber)
      }
      loop(EventNumber.First).map(_.eventRecord.event)
    }


    def expectEventAppeared(testKit: TestKitBase = this) = {
      val resolvedEvent = testKit.expectMsgType[StreamEventAppeared].resolvedEvent
      resolvedEvent.eventRecord.streamId mustEqual streamId
      resolvedEvent
    }

    def mustBeSorted[T](xs: Seq[T])(implicit direction: ReadDirection.Value, ordering: Ordering[T]) {
      xs must beSorted(direction match {
        case Forward => ordering
        case Backward => ordering.reverse
      })
    }

    def readAllEventsSucceed(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction)
      val result = expectMsgType[ReadAllEventsSucceed]
      result.direction mustEqual direction

      val resolvedEvents = result.resolvedEvents
      resolvedEvents.size must beLessThanOrEqualTo(maxCount)
      resolvedEvents.foreach {
        x =>
          x.link must beEmpty
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

    def allStreamsEventRecords(maxCount: Int = 500)(implicit direction: ReadDirection.Value): Stream[EventRecord] = {
      def loop(position: Position): Stream[ResolvedEvent] = {
        val result = readAllEventsSucceed(position, maxCount)
        val resolvedEvents = result.resolvedEvents
        if (resolvedEvents.isEmpty) Stream()
        else resolvedEvents.toStream #::: loop(result.nextPosition)
      }
      val position = direction match {
        case Forward => Position.First
        case Backward => Position.Last
      }
      loop(position).map(_.eventRecord)
    }

    def allStreamsEvents(maxCount: Int = 500)(implicit direction: ReadDirection.Value) =
      allStreamsEventRecords(maxCount).map(_.event)

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

