package eventstore
package akka
package tcp

import scala.util.Success
import scala.collection.mutable.{Queue => MQueue}
import _root_.akka.stream.scaladsl._
import _root_.akka.stream.{ActorMaterializer, OverflowStrategy}
import _root_.akka.testkit.TestProbe
import eventstore.tcp.{PackIn, PackOut}
import testutil.TestLogger

class BidiLoggingSpec extends ActorSpec {

  implicit val materializer = ActorMaterializer()

  "BidiLogging" should {

    "log incoming & outgoing if enabled" in new TestScope {
      val packIn = PackIn(Authenticated)
      val packOut = PackOut(Authenticate, packIn.correlationId)

      source ! packIn
      sink.expectMsg(packOut)

      debugLog    shouldEqual List(packIn.toString, packOut.toString)
      debugChecks shouldEqual 4 // Same check happens inside debug call, hence 4 instead of 2.
    }

    "not log incoming & outgoing if disabled" in new TestScope {
      override def turnOffLogging = true
      source ! PackIn(Authenticated)
      sink.expectMsgType[PackOut].message shouldEqual Authenticate
      debugLog.size shouldEqual 0
      debugChecks   shouldEqual 2
    }

    "not log Pong & Ping" in new TestScope {
      source ! PackIn(Ping)
      sink.expectMsgType[PackOut].message shouldEqual Pong
      debugLog.size shouldEqual 0
      debugChecks   shouldEqual 2
    }

    "not log HeartbeatRequest & HeartbeatResponse" in new TestScope {
      source ! PackIn(HeartbeatRequest)
      sink.expectMsgType[PackOut].message shouldEqual HeartbeatResponse
      debugLog.size shouldEqual 0
      debugChecks   shouldEqual 2
    }
  }

  private trait TestScope extends ActorScope {

    import TestLogger.Item

    def turnOffLogging: Boolean = false

    private var dc  = 0
    private val ls  = MQueue.empty[Item.Debug]
    private val log = TestLogger.debug(ls.enqueue(_), { dc = dc + 1 })

    final def debugChecks: Int       = dc
    final def debugLog: List[String] = ls.toList.map(_.msg)

    private def logAdapter = if(turnOffLogging) log.turnOff else log

    val logging = BidiLogging(logAdapter)
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
