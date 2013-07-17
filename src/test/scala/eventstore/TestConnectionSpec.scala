package eventstore.client

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.ActorSystem
import eventstore.client.tcp.{UuidSerializer, ConnectionActor}
import java.net.InetSocketAddress
import org.specs2.mutable.{SpecificationWithJUnit, After}
import eventstore.client.OperationResult._


/**
 * @author Yaroslav Klymko
 */

abstract class TestConnectionSpec extends SpecificationWithJUnit {

  abstract class TestConnectionScope extends TestKit(ActorSystem()) with After with ImplicitSender {
    val streamId = getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString

    val streamMetadata = ByteString(getClass.getEnclosingClass.getSimpleName)
    val actor = TestActorRef(new ConnectionActor(new InetSocketAddress("127.0.0.1", 1113)))


    def createStream = CreateStream(streamId, newUuid, streamMetadata, allowForwarding = true, isJson = false)

    def createStreamCompleted = CreateStreamCompleted(Success, None)

    def deleteStream(expVer: ExpectedVersion = AnyVersion) = DeleteStream(streamId, expVer, allowForwarding = true)

    def deleteStreamCompleted = DeleteStreamCompleted(Success, None)

    def eventRecord(eventNumber: Int, event: NewEvent) =
      EventRecord(streamId, eventNumber, event.eventId, event.eventType, event.data, event.metadata)

    def readStreamEvents = ReadStreamEvents(streamId, 0, 1000, resolveLinkTos = false, ReadDirection.Forward)

    def newEvent = NewEvent(UuidSerializer.serialize(newUuid), Some("test"), isJson = false, ByteString.empty, Some(ByteString("test")))

    def writeEvents(expVer: ExpectedVersion, events: NewEvent*) =
      WriteEvents(streamId, expVer, events.toList, allowForwarding = true)

    def writeEventsCompleted(firstEventNumber: Int = 0) = WriteEventsCompleted(Success, None, firstEventNumber)


    def after = {
      /*println("after")
      try actor ! DeleteStream(streamId, AnyVersion, allowForwarding = true)
      catch {
        case e: Throwable =>
      }*/

      // TODO
    }
  }

}

