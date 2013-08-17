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
    val streamId = StreamId(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(Settings()))

    def deleteStream(expVer: ExpectedVersion = AnyVersion) {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, expVer, requireMaster = true))(probe.ref)
      probe.expectMsg(DeleteStreamSucceed)
    }

    def appendEventToCreateStream() {
      val probe = TestProbe()
      actor.!(appendToStream(NoStream, Event(newUuid, "first event")))(probe.ref)
      probe.expectMsg(AppendToStreamSucceed(0))
    }

    def newEvent = {
      val bytes = new Array[Byte](10)
      Random.nextBytes(bytes)
      val bs = ByteString(bytes)
      Event(newUuid, "test", data = bs, metadata = bs)
    }

    def appendToStream(expVer: ExpectedVersion, events: Event*) = AppendToStream(streamId, expVer, events.toList)

    def appendToStreamSucceed(firstEventNumber: Int = 0) = AppendToStreamSucceed(firstEventNumber)

    def doAppendToStream(event: Event,
                         expVer: ExpectedVersion = AnyVersion,
                         firstEventNumber: Int = 0,
                         testKit: TestKitBase = this) {
      actor.!(appendToStream(expVer, event))(testKit.testActor)
      testKit.expectMsg(appendToStreamSucceed(firstEventNumber))
    }


    def readStreamEventsSucceed(fromEventNumber: Int, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val result = expectMsgType[ReadStreamEventsSucceed]
      result.direction mustEqual direction

      val resolvedIndexedEvents = result.resolvedIndexedEvents
      val size = resolvedIndexedEvents.size
      size must beLessThanOrEqualTo(maxCount)

      if (resolvedIndexedEvents.nonEmpty) {

        val next = result.nextEventNumber
        val (min, max, ordering) = {
          val ordering = implicitly[Ordering[ResolvedIndexedEvent]]
          direction match {
            case Forward => (fromEventNumber, next, ordering)
            case Backward => (next, fromEventNumber, ordering.reverse)
          }
        }
        min must beLessThanOrEqualTo(max)

        next mustEqual (direction match {
          case Forward =>
            if (result.endOfStream) result.lastEventNumber + 1 else fromEventNumber + size

          case Backward =>
            if (result.endOfStream) -1 else fromEventNumber - size
        })

        resolvedIndexedEvents.size must lessThanOrEqualTo(maxCount)
        resolvedIndexedEvents must beSorted(ordering)
        resolvedIndexedEvents.foreach {
          x =>
            x.link must beEmpty
            x.eventRecord.number.value must beGreaterThanOrEqualTo(min)
            x.eventRecord.number.value must beLessThanOrEqualTo(max)
        }
      }

      result
    }

    def readStreamEvents(fromEventNumber: Int, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      val result = readStreamEventsSucceed(fromEventNumber = fromEventNumber, maxCount = maxCount)
      result.resolvedIndexedEvents.map(_.eventRecord.event)
    }


    def readStreamEventsFailed(fromEventNumber: Int, maxCount: Int)
                              (implicit direction: ReadDirection.Value): ReadStreamEventsFailed = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val failed = expectMsgType[ReadStreamEventsFailed]
      failed.direction mustEqual direction
      failed
    }

    def readStreamEventsFailed(implicit direction: ReadDirection.Value): ReadStreamEventsFailed = {
      val position = direction match {
        case Forward => 0 // TODO
        case Backward => -1 // TODO
      }
      readStreamEventsFailed(position, 500)
    }


    def streamEvents: Stream[Event] = {
      implicit val direction = Forward
      def loop(position: Int): Stream[ResolvedIndexedEvent] = {
        val result = readStreamEventsSucceed(position, 500)
        val resolvedIndexedEvents = result.resolvedIndexedEvents
        if (resolvedIndexedEvents.isEmpty || result.endOfStream) resolvedIndexedEvents.toStream
        else resolvedIndexedEvents.toStream #::: loop(result.nextEventNumber)
      }
      loop(0).map(_.eventRecord.event)
    }

    def append(events: Event*) {
      actor ! AppendToStream(streamId, AnyVersion, events.toList)
      expectMsg(appendToStreamSucceed())
    }

    def appendMany(size: Int = 10, testKit: TestKitBase = this): Seq[Event] = {
      val duration = FiniteDuration(10, SECONDS)
      val events = (1 to size).map(_ => newEvent)

      def loop(n: Int) {
        actor.!(AppendToStream(streamId, AnyVersion, events.toList))(testKit.testActor)
        testKit.expectMsgPF(duration) {
          case AppendToStreamSucceed(_) => true
          case AppendToStreamFailed(OperationFailed.PrepareTimeout, _) if n < 3 => loop(n + 1)
        }
      }

      loop(0)
      events
    }

    def expectEventAppeared(testKit: TestKitBase = this) = {
      val resolvedEvent = testKit.expectMsgType[StreamEventAppeared].resolvedEvent
      resolvedEvent.eventRecord.streamId mustEqual streamId
      resolvedEvent
    }

    def readAllEventsSucceed(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadAllEvents(position, maxCount, direction)
      val result = expectMsgType[ReadAllEventsSucceed]
      result.direction mustEqual direction

      val resolvedEvents = result.resolvedEvents

      if (resolvedEvents.nonEmpty) {
        val nextPosition = result.nextPosition
        val (min, max, ordering) = {
          val ordering = implicitly[Ordering[ResolvedEvent]]
          direction match {
            case Forward => (position, nextPosition, ordering)
            case Backward => (nextPosition, position, ordering.reverse)
          }
        }
        min must beLessThanOrEqualTo(max)

        resolvedEvents.size must beLessThanOrEqualTo(maxCount)
        resolvedEvents must beSorted(ordering)
        resolvedEvents.foreach {
          x =>
            x.position must beLessThanOrEqualTo(max)
            x.position must beGreaterThanOrEqualTo(min)
            x.link must beEmpty
        }
      }
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
      /*println("after")
      try actor ! DeleteStream(streamId, AnyVersion, requireMaster = true)
      catch {
        case e: Throwable =>
      }*/

      // TODO
    }
  }

}

