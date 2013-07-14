package eventstore.client

import org.specs2.mutable.SpecificationWithJUnit
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.actor.ActorSystem
import org.specs2.specification.Scope
import eventstore.client.tcp.{UuidSerializer, ConnectionActor}
import OperationResult._

import java.net.InetSocketAddress
import akka.io.Tcp

/**
 * @author Yaroslav Klymko
 */
class AllSpec extends TestConnectionSpec {
  "all" should {
    "create stream" in new TestConnectionScope {
      actor ! CreateStream(streamId, newUuid, ByteString.empty, allowForwarding = true, isJson = false)
      expectMsg(CreateStreamCompleted(Success, None))

      actor ! CreateStream(streamId, newUuid, ByteString.empty, allowForwarding = true, isJson = false)
      expectMsgPF() {
        case CreateStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }



//      actor ! CreateStream(streamId, newUuid, ByteString.empty, allowForwarding = true, isJson = false)
//      expectMsgPF() {
//        case CreateStreamCompleted(WrongExpectedVersion, Some(_)) => true
//      }

      val events = List(newEvent, newEvent)
      actor ! WriteEvents(streamId, NoStream, events, allowForwarding = true)
      //      expectMsg(WriteEventsCompleted(Success, None, 1))
      expectMsgPF() {
        case WriteEventsCompleted(WrongExpectedVersion, Some(_), _) => true
      }

      // TODO implement buffering and resending not ack-ed messages
      /*actor ! WriteEvents(streamId, EmptyStream, Nil, allowForwarding = true)
      //      expectMsg(WriteEventsCompleted(Success, None, 1))
      expectMsg(BadRequest)

      //      val events = List(newEvent, newEvent)
      actor ! WriteEvents(streamId, EmptyStream, events, allowForwarding = true)
      actor ! WriteEvents(streamId, EmptyStream, events, allowForwarding = true)
      expectMsgType[Tcp.Connected]

      expectMsg(WriteEventsCompleted(Success, None, 1))
      expectMsg(WriteEventsCompleted(Success, None, 1))*/

      actor ! WriteEvents(streamId, EmptyStream, events, allowForwarding = true)
      expectMsg(WriteEventsCompleted(Success, None, 1))


      actor ! DeleteStream(streamId, EmptyStream, allowForwarding = true)
      expectMsgPF() {
        case DeleteStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }


      actor ! DeleteStream(streamId, Version(events.size), allowForwarding = true)
      expectMsg(DeleteStreamCompleted(Success, None))


      actor ! DeleteStream(streamId, AnyVersion, allowForwarding = true)
      expectMsgPF() {
        case DeleteStreamCompleted(StreamDeleted, Some(_)) => true
      }

      //      actor ! DeleteStream(streamId, AnyVersion, allowForwarding = true)
      //      expectMsg(DeleteStreamCompleted(Success, None))


      actor ! CreateStream(streamId, newUuid, ByteString.empty, allowForwarding = true, isJson = false)
      //      expectMsg(CreateStreamCompleted(Success, None))
      expectMsgPF() {
        case CreateStreamCompleted(StreamDeleted, Some(_)) => true
      }

      actor ! WriteEvents(streamId, AnyVersion, events, allowForwarding = true)
      expectMsgPF() {
        case WriteEventsCompleted(StreamDeleted, Some(_), _) => true
      }
    }
  }
}
