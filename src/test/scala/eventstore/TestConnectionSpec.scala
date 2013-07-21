package eventstore

import akka.testkit._
import akka.actor.ActorSystem
import tcp.ConnectionActor
import java.net.InetSocketAddress
import org.specs2.mutable.{SpecificationWithJUnit, After}
import eventstore.OperationResult._


/**
 * @author Yaroslav Klymko
 */

abstract class TestConnectionSpec extends SpecificationWithJUnit {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = Stream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(new InetSocketAddress("127.0.0.1", 1113)))

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

    def newEvent = Event(newUuid, "test")

    def appendToStream(expVer: ExpectedVersion, events: Event*) = AppendToStream(streamId, expVer, events.toList)

    def appendToStreamCompleted(firstEventNumber: Int = 0) = AppendToStreamSucceed(firstEventNumber)

    def doAppendToStream(event: Event,
                         expVer: ExpectedVersion = AnyVersion,
                         firstEventNumber: Int = 0,
                         testKit: TestKitBase = this) {
      actor.!(appendToStream(expVer, event))(testKit.testActor)
      testKit.expectMsg(appendToStreamCompleted(firstEventNumber))
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
      expectMsg(appendToStreamCompleted())
    }

    def appendMany(kit: TestKitBase = this): Seq[Event] = {
      val events = (1 to 10).map(_ => newEvent)
      actor.!(AppendToStream(streamId, AnyVersion, events.toList))(kit.testActor)
      kit.expectMsg(appendToStreamCompleted())
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

