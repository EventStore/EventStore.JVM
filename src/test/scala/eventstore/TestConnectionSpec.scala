package eventstore

import akka.testkit._
import akka.actor.ActorSystem
import tcp.ConnectionActor
import org.specs2.mutable.{SpecificationWithJUnit, After}
import org.specs2.time.NoDurationConversions
import scala.concurrent.duration._
import scala.util.Random


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
      val size = result.events.size

      size must beLessThanOrEqualTo(maxCount)

      if (result.events.nonEmpty) {
        val next = direction match {
          case ReadDirection.Forward =>
            if (result.endOfStream) result.lastEventNumber + 1 else fromEventNumber + size

          case ReadDirection.Backward =>
            if (result.endOfStream) -1 else fromEventNumber - size
        }
        result.nextEventNumber mustEqual next
      }
      result
    }

    def readStreamEvents(fromEventNumber: Int, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      val result = readStreamEventsSucceed(fromEventNumber = fromEventNumber, maxCount = maxCount)
      result.events.map(_.eventRecord.event)
    }


    def readStreamEventsFailed(fromEventNumber: Int, maxCount: Int)(implicit direction: ReadDirection.Value) = {
      actor ! ReadStreamEvents(streamId, fromEventNumber, maxCount, direction)
      val failed = expectMsgType[ReadStreamEventsFailed]
      failed.direction mustEqual direction
      failed
    }


    def streamEvents: Seq[Event] = {
      // TODO make more solid
      actor ! ReadStreamEvents(streamId, 0, 1000, ReadDirection.Forward)
      expectMsgType[ReadStreamEventsSucceed].events.map(_.eventRecord.event)
      //      expectMsgPF() {
      //        case ReadStreamEventsCompleted(events, ReadStreamEventsFailed.Success, _, _, _, _, _) => events.map(_.eventRecord.event)
      //        case ReadStreamEventsCompleted(Nil, ReadStreamEventsFailed.NoStream, _, _, _, _, _) => Nil
      //      }
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
      val succeed = expectMsgType[ReadAllEventsSucceed]
      succeed.direction mustEqual direction
      succeed
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

    def readAllEvents(position: Position, maxCount: Int)(implicit direction: ReadDirection.Value): Seq[Event] =
      readAllEventRecords(position, maxCount).map(_.event)

    def readUntilEndOfStream(size: Int)(implicit direction: ReadDirection.Value) {
      def read(position: Position) {
        actor ! ReadAllEvents(position, size, direction)
        val (events, nextPosition) = expectMsgPF() {
          case ReadAllEventsSucceed(_, xs, p, `direction`) => (xs, p)
        }
        if (events.nonEmpty) {
          events.size must beLessThanOrEqualTo(size)
          read(nextPosition)
        }
      }
      val position = direction match {
        case ReadDirection.Forward => Position.start
        case ReadDirection.Backward => Position.end
      }
      read(position)
    }

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

