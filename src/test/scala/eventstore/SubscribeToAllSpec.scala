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
      val clients = List(TestProbe(), TestProbe(), TestProbe()).map(client => client -> subscribeToAll(testKit = client))

      val event = newEvent
      append(event)

      clients.foreach {
        case (client, commitPosition) =>
          val resolvedEvent = expectEventAppeared(client)
          resolvedEvent.position.commitPosition must >(commitPosition)
          resolvedEvent.eventRecord.number mustEqual EventNumber(1)
      }
    }

    "catch created and deleted events as well" in new SubscribeToAll {
      val lastCommitPosition = subscribeToAll()
      appendEventToCreateStream()
      expectEventAppeared().eventRecord.number mustEqual EventNumber.First
      deleteStream()

      val resolvedEvent = expectEventAppeared()
      resolvedEvent.position.commitPosition must >(lastCommitPosition)
      val eventRecord = resolvedEvent.eventRecord
      resolvedEvent.eventRecord must beLike {
        case EventRecord.StreamDeleted(`streamId`, EventNumber.Exact(Int.MaxValue /*TODO WHY?*/), _) => ok
      }
    }

    "not catch linked events if resolveLinkTos = false" in new SubscribeToAll {
      subscribeToAll(resolveLinkTos = false)
      val (linked, link) = linkedAndLink()

      expectEventAppeared()
      expectEventAppeared()
      val resolvedEvent = expectEventAppeared()
      resolvedEvent.eventRecord mustEqual link
      resolvedEvent.link must beNone
    }

    "catch linked events if resolveLinkTos = true" in new SubscribeToAll {
      subscribeToAll(resolveLinkTos = true)
      val (linked, link) = linkedAndLink()

      expectEventAppeared()
      expectEventAppeared()
      val resolvedEvent = expectEventAppeared()
      resolvedEvent.eventRecord mustEqual linked
      resolvedEvent.link must beSome(link)
    }
  }

  trait SubscribeToAll extends TestConnectionScope {
    def subscribeToAll(resolveLinkTos: Boolean = false, testKit: TestKitBase = this) = {
      actor.!(SubscribeTo(EventStream.All, resolveLinkTos = resolveLinkTos))(testKit.testActor)
      testKit.expectMsgType[SubscribeToAllCompleted].lastCommit
    }
  }
}
