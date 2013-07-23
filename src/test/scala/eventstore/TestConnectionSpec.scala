package eventstore

import akka.testkit._
import akka.actor.ActorSystem
import tcp.ConnectionActor
import org.specs2.mutable.{SpecificationWithJUnit, After}
import scala.concurrent.duration._
import org.specs2.time.NoDurationConversions
import scala.util.Random


/**
 * @author Yaroslav Klymko
 */
abstract class TestConnectionSpec extends SpecificationWithJUnit with NoDurationConversions {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

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

    def readStreamEvents = ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, ReadDirection.Forward)

    def newEvent = {
      val bytes = new Array[Byte](100)
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

    def streamEvents: List[Event] = {
      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, _, _, _, _, _) => events.map(_.eventRecord.event)
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, _, _, _, _, _) => Nil
      }
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

    def expectEventAppeared(eventNumber: EventNumber.Exact, testKit: TestKitBase = this) = testKit.expectMsgPF() {
      case StreamEventAppeared(ResolvedEvent(EventRecord(`streamId`, `eventNumber`, event), None, _, _)) => event
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

