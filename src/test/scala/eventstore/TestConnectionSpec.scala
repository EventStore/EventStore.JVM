package eventstore

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
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


    def createStream = CreateStream(streamId, newUuid, streamMetadata, requireMaster = true, isJson = false)

    def createStreamCompleted = CreateStreamCompleted(Success, None)

    def deleteStream(expVer: ExpectedVersion = AnyVersion) = DeleteStream(streamId, expVer, requireMaster = true)

    def deleteStreamCompleted = DeleteStreamCompleted(Success, None)

    def eventRecord(eventNumber: Int, event: NewEvent) =
      EventRecord(streamId, eventNumber, event.eventId, event.eventType, event.data, event.metadata)

    def readStreamEvents = ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, ReadDirection.Forward)

    def newEvent = NewEvent(UuidSerializer.serialize(newUuid), "test", isJson = false, ByteString.empty, Some(ByteString("test")))

    def writeEvents(expVer: ExpectedVersion, events: NewEvent*) =
      WriteEvents(streamId, expVer, events.toList, requireMaster = true)

    def writeEventsCompleted(firstEventNumber: Int = 0) = WriteEventsCompleted(Success, None, firstEventNumber)


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

