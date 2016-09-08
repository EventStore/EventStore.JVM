package eventstore

import akka.actor.Status
import akka.stream.StreamTcpException
import akka.testkit.TestProbe
import eventstore.tcp.ConnectionActor

abstract class AbstractSubscriptionActorITest extends util.ActorSpec {
  trait TestScope extends ActorScope {
    val connection = system.actorOf(ConnectionActor.props(settings))
    val streamId = EventStream.Id(randomUuid.toString)

    def write(count: Int = 1): Unit = {
      def event = EventData("test")
      val probe = TestProbe()
      connection.tell(WriteEvents(streamId, List.fill(count)(event)), probe.ref)
      probe.expectMsgType[WriteEventsCompleted]
    }

    def expectLiveProcessingStarted = expectMsg(LiveProcessingStarted)

    def reconnect() = connection ! Status.Failure(new StreamTcpException("peer closed"))

    def settings = Settings.Default
  }
}
