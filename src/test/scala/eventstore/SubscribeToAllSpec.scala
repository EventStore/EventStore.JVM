package eventstore

import akka.testkit.{TestKitBase, TestProbe}

/**
 * @author Yaroslav Klymko
 */
class SubscribeToAllSpec extends TestConnectionSpec {
  sequential

  "subscribe to all" should {

    "allow multiple subscriptions" in new SubscribeToAll {
      appendEventToCreateStream()
      val clients = List(TestProbe(), TestProbe(), TestProbe()).map(client => client -> subscribeToAll(client))

      val event = newEvent
      doAppendToStream(event, AnyVersion, 1)

      clients.foreach {
        case (client, commitPosition) =>
          val resolvedEvent = expectEventAppeared(client)
          resolvedEvent.position.commitPosition must >(commitPosition)
          resolvedEvent.eventRecord.number mustEqual EventNumber.Exact(1)
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      val lastCommitPosition = subscribeToAll()
      appendEventToCreateStream()
      expectEventAppeared().eventRecord.number mustEqual EventNumber.First
      deleteStream()

      val resolvedEvent = expectEventAppeared()
      val eventRecord = resolvedEvent.eventRecord
      resolvedEvent.position.commitPosition must >(lastCommitPosition)
      eventRecord.number mustEqual EventNumber.Max
      eventRecord.event must beLike {
        case Event.StreamDeleted(_) => ok
      }
    }
  }

  trait SubscribeToAll extends TestConnectionScope {
    def subscribeToAll(testKit: TestKitBase = this) = {
      actor.!(SubscribeTo(AllStreams))(testKit.testActor)
      testKit.expectMsgType[SubscribeToAllCompleted].lastCommit
    }
  }
}
