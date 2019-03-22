package eventstore
package akka

class EventStoreExtensionITest extends ActorSpec {

  "EventStoreExtension" should {

    "return connection actor" in new ActorScope {
      EventStoreExtension(system).actor ! Ping
      expectMsg(Pong)
    }

    "return connection instance" in new ActorScope {
      EventStoreExtension(system).connection(Ping).await_ mustEqual Pong
    }

  }
}
