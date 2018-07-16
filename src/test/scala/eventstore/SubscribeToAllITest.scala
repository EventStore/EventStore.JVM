package eventstore

import akka.testkit.{ TestKitBase, TestProbe }

class SubscribeToAllITest extends TestConnection {
  sequential

  "subscribe to all" should {

    "allow multiple subscriptions" in new SubscribeToAll {
      appendEventToCreateStream()
      val clients = List(TestProbe(), TestProbe(), TestProbe()).map(client => client -> subscribeToAll(testKit = client))

      val eventData = newEventData
      append(eventData)

      clients.foreach {
        case (client, commitPosition) =>
          val indexedEvent = expectNonSystemStreamEventAppeared(client)
          indexedEvent.position.commitPosition must >(commitPosition)
          val event = indexedEvent.event
          event.number mustEqual EventNumber(1)
          event.data mustEqual eventData
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      val lastCommitPosition = subscribeToAll()
      appendEventToCreateStream()
      expectNonSystemStreamEventAppeared().event.number mustEqual EventNumber.First
      deleteStream()

      val indexedEvent = expectNonSystemStreamEventAppeared()
      indexedEvent.position.commitPosition must >(lastCommitPosition)
      indexedEvent.event must beLike {
        case Event.StreamDeleted() => ok
      }
    }

    "not catch linked events if resolveLinkTos = false" in new SubscribeToAll {
      subscribeToAll(resolveLinkTos = false)
      val (linked, link) = linkedAndLink()
      expectNonSystemStreamEventAppeared().event mustEqual linked
      expectNonSystemStreamEventAppeared()
      expectNonSystemStreamEventAppeared().event mustEqual link
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeToAll {
      subscribeToAll(resolveLinkTos = true)
      val (linked, link) = linkedAndLink()
      expectNonSystemStreamEventAppeared().event mustEqual linked
      expectNonSystemStreamEventAppeared()
      expectNonSystemStreamEventAppeared().event mustEqual ResolvedEvent(linked, link)
    }
  }

  private trait SubscribeToAll extends TestConnectionScope {
    def subscribeToAll(resolveLinkTos: Boolean = false, testKit: TestKitBase = this) = {
      actor.!(SubscribeTo(EventStream.All, resolveLinkTos = resolveLinkTos))(testKit.testActor)
      testKit.expectMsgType[SubscribeToAllCompleted].lastCommit
    }
  }
}
