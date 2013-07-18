package eventstore

import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.actor.ActorSystem
import tcp.{UuidSerializer, ConnectionActor}
import java.net.InetSocketAddress
import org.specs2.mutable.{SpecificationWithJUnit, After}
import eventstore.OperationResult._


/**
 * @author Yaroslav Klymko
 */

abstract class TestConnectionSpec extends SpecificationWithJUnit {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(new InetSocketAddress("127.0.0.1", 1113)))

    def deleteStream(expVer: ExpectedVersion = AnyVersion) {
      val probe = TestProbe()
      actor.!(DeleteStream(streamId, expVer, requireMaster = true))(probe.ref)
      probe.expectMsg(DeleteStreamCompleted(Success, None))
    }

    def createStream() {
      val probe = TestProbe()
      actor.!(appendToStream(NoStream, Event(newUuid, "create stream")))(probe.ref)
      probe.expectMsg(AppendToStreamCompleted(Success, None, 0))
      //            actor ! createStream
      //      expectMsg(createStreamCompleted)
    }

    def eventRecord(eventNumber: Int, event: Event) = EventRecord(streamId, eventNumber, event)

    def readStreamEvents = ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, ReadDirection.Forward)

    def newEvent = Event(newUuid, "test")

    def appendToStream(expVer: ExpectedVersion, events: Event*) = AppendToStream(streamId, expVer, events.toList)

    def appendToStreamCompleted(firstEventNumber: Int = 0) = AppendToStreamCompleted(Success, None, firstEventNumber)

    def doAppendToStream(event: Event, expVer: ExpectedVersion = AnyVersion, firstEventNumber: Int = 0) {
      actor ! appendToStream(expVer, event)
      expectMsg(appendToStreamCompleted(firstEventNumber))
    }

    def streamEvents: List[Event] = {
      actor ! readStreamEvents
      expectMsgPF() {
        case ReadStreamEventsCompleted(events, ReadStreamResult.Success, _, _, _, _, _) => events.map(_.eventRecord.event)
        case ReadStreamEventsCompleted(Nil, ReadStreamResult.NoStream, _, _, _, _, _) => Nil
      }
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

