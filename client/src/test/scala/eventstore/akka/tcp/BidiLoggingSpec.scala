package eventstore
package akka
package tcp

import scala.util.Success
import org.specs2.mock.Mockito
import _root_.akka.event.LoggingAdapter
import _root_.akka.stream.scaladsl._
import _root_.akka.stream.{ActorMaterializer, OverflowStrategy}
import _root_.akka.testkit.TestProbe
import eventstore.tcp.{PackIn, PackOut}

class BidiLoggingSpec extends ActorSpec with Mockito {

  implicit val materializer = ActorMaterializer()

  "BidiLogging" should {

    "log incoming & outgoing if enabled" in new TestScope {
      val packIn = PackIn(Authenticated)
      val packOut = PackOut(Authenticate, packIn.correlationId)
      source ! packIn
      sink.expectMsg(packOut)
      there was one(log).debug(packIn.toString)
      there was one(log).debug(packOut.toString)
    }

    "not log incoming & outgoing if disabled" in new TestScope {
      log.isDebugEnabled returns false
      source ! PackIn(Authenticated)
      sink.expectMsgType[PackOut].message shouldEqual Authenticate
      there was two(log).isDebugEnabled
      there were noMoreCallsTo(log)
    }

    "not log Pong & Ping" in new TestScope {
      source ! PackIn(Ping)
      sink.expectMsgType[PackOut].message shouldEqual Pong
      there was two(log).isDebugEnabled
      there were noMoreCallsTo(log)
    }

    "not log HeartbeatRequest & HeartbeatResponse" in new TestScope {
      source ! PackIn(HeartbeatRequest)
      sink.expectMsgType[PackOut].message shouldEqual HeartbeatResponse
      there was two(log).isDebugEnabled
      there were noMoreCallsTo(log)
    }
  }

  private trait TestScope extends ActorScope {
    val log = {
      val log = mock[LoggingAdapter]
      log.isDebugEnabled returns true
      log
    }
    val logging = BidiLogging(log)
    val sink = TestProbe()
    val flow = Flow.fromFunction[PackIn, PackOut] {
      case PackIn(Success(Ping), correlationId)             => PackOut(Pong, correlationId)
      case PackIn(Success(HeartbeatRequest), correlationId) => PackOut(HeartbeatResponse, correlationId)
      case PackIn(Success(Authenticated), correlationId)    => PackOut(Authenticate, correlationId)
      case PackIn(message, _)                               => sys error s"unexpected $message"
    }
    val (source, _) = (logging join flow).runWith(
      Source.actorRef(100, OverflowStrategy.fail),
      Sink.actorRef[PackOut](sink.ref, "completed")
    )
  }
}
