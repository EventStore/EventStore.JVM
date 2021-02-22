package eventstore
package akka
package cluster

import java.net.InetSocketAddress
import java.time.ZonedDateTime
import scala.concurrent.Future
import scala.util.control.NoStackTrace
import scala.concurrent.duration._
import _root_.akka.testkit.TestProbe
import _root_.akka.util.Timeout
import _root_.akka.actor.Status.Failure
import _root_.akka.pattern.ask
import eventstore.core.syntax._
import eventstore.core.cluster._
import eventstore.core.cluster.GossipSeedsOrDns.ClusterDns
import eventstore.core.cluster.NodeState._
import eventstore.core.settings.ClusterSettings
import ClusterDiscovererActor._

class ClusterDiscovererActorSpec extends ActorSpec {
  "ClusterDiscovererActor" should {
    "discover cluster from dns" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address2))

      expectMsg(Address(address2))

      override def settings = super.settings.copy(gossipSeedsOrDns = ClusterDns("127.0.0.1", 1))
    }

    "fail if cannot discover cluster from dns" in new TestScope {
      actor ! GetAddress()

      expectFailure

      override def settings = super.settings.copy(
        gossipSeedsOrDns = ClusterDns("nobody"),
        dnsLookupTimeout = 300.millis
      )
    }

    "re-discover cluster each second" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster()
      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone), member(address3, Clone))

      expectMsg(Address(address))

      expectMsg(address)
      lastSender ! cluster(member(address, Clone), member(address2), member(address3, Clone))

      expectMsg(Address(address2))

      expectMsg(address2)
      lastSender ! cluster(member(address, Clone), member(address2, Clone), member(address3))

      expectMsg(Address(address3))
    }

    "re-discover cluster until max attempts reached if no best node found" in new TestScope {
      actor ! GetAddress()

      expectMsgPF() {
        case `address` =>
          lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown), member(address3, Shutdown))
          expectMsgPF() {
            case `address2` =>
              lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown), member(address3, Shutdown))
          }

        case `address2` =>
          lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown), member(address3, Shutdown))
          expectMsgPF() {
            case `address` =>
              lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown), member(address3, Shutdown))
          }
      }

      expectMsgPF() {
        case `address` =>
          lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown))
          expectMsgPF() {
            case `address2` => lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown))
          }

        case `address2` =>
          lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown))
          expectMsgPF() {
            case `address` => lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown))
          }
      }

      expectMsgPF() {
        case `address` =>
          lastSender ! cluster(member(address, Manager))
          expectMsgPF() { case `address2` => lastSender ! cluster(member(address, Manager)) }

        case `address2` =>
          lastSender ! cluster(member(address, Manager))
          expectMsgPF() { case `address` => lastSender ! cluster(member(address, Manager)) }
      }

      expectFailure

      override def settings = super.settings.copy(gossipSeedsOrDns = GossipSeedsOrDns.GossipSeeds(address, address2))
    }

    "re-discover cluster until max attempts reached" in new TestScope {
      actor ! GetAddress()
      expectMsg(address)
      lastSender ! cluster()

      expectMsg(address)
      lastSender ! cluster()

      expectMsg(address)
      lastSender ! cluster()

      expectFailure
    }

    "re-discover cluster until max attempts reached even if failures received" in new TestScope {
      actor ! GetAddress()
      expectMsg(address)
      lastSender ! cluster()

      expectMsg(address)
      lastSender ! failure

      expectMsg(address)
      lastSender ! failure

      expectFailure
    }

    "return best node if exists" in new TestScope {
      actor // force init
      expectMsg(address)
      lastSender ! cluster()
      expectMsg(address)
      lastSender ! cluster(member(address))
      actor ! GetAddress()
      expectMsg(Address(address))
    }

    "return best node once" in new TestScope {
      actor ! GetAddress()
      expectMsg(address)
      lastSender ! cluster(member(address))

      actor ! GetAddress()
      actor ! GetAddress()
      expectMsgAllOf(Address(address), Address(address), Address(address))

      expectMsg(address)
      lastSender ! cluster(member(address2))

      expectMsg(Address(address2))
      expectNoMessage(100.millis)
    }

    "return best node to all clients" in new TestScope {
      val clients = List.fill(5)(TestProbe())
      clients.foreach { client => actor.tell(GetAddress(), client.ref) }

      expectMsg(address)
      lastSender ! cluster(member(address))
      clients.foreach { client => client expectMsg Address(address) }

      expectMsg(address)
      lastSender ! cluster(member(address2))

      clients.foreach { client => client expectMsg Address(address2) }
    }

    "return best node after discovered" in new TestScope {
      actor ! GetAddress()
      expectMsg(address)
      lastSender ! cluster()
      expectMsg(address)
      lastSender ! cluster(member(address))
      expectMsg(Address(address))
    }

    "re-discover if bestNode failure reported by client" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))

      expectMsg(Address(address))
      actor ! GetAddress(Some(address))

      expectMsg(address2)
      lastSender ! cluster(member(address2), member(address3, Clone))

      expectMsg(Address(address2))
      actor ! GetAddress(Some(address2))

      expectMsg(address3)
      lastSender ! cluster(member(address, Clone), member(address2, Clone), member(address3))

      expectMsg(Address(address3))
    }

    "re-discover if bestNode failure reported by client" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address))

      expectMsg(Address(address))
      actor ! GetAddress(Some(address))

      expectMsg(address)
      lastSender ! cluster(member(address2), member(address3, Clone))

      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))
      expectMsg(Address(address))

      expectMsg(address)
      actor ! GetAddress(Some(address))
      lastSender ! cluster(member(address, ShuttingDown), member(address2))

      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))
      expectMsg(Address(address))

      expectMsg(address)
      actor ! GetAddress(Some(address))
      lastSender ! cluster()

      expectMsg(address2)
      lastSender ! cluster(member(address2))

      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))
      expectMsg(Address(address))

      expectMsg(address)
      actor ! GetAddress(Some(address))
      lastSender ! failure

      expectMsg(address2)
      lastSender ! cluster(member(address2))

      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))
      expectMsg(Address(address))

      expectMsg(address)
      actor ! GetAddress(Some(address))
      lastSender ! cluster(member(address), member(address2, Clone))

      expectMsg(address2)
      lastSender ! cluster(member(address2))
      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address2))
      expectMsg(Address(address2))

      expectMsg(address2)
      actor ! GetAddress(Some(address2))
      lastSender ! cluster(member(address2))

      expectMsg(address)
      lastSender ! cluster(member(address2))
      expectMsg(Address(address2))
    }

    "re-discover if bestNode failure reported by client while discovering" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address2))
      expectMsg(Address(address2))

      expectMsg(address2)
      actor ! GetAddress(Some(address2))
      lastSender ! cluster()

      expectMsg(address)
      lastSender ! cluster()
    }

    "re-discover if bestNode failed with error" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address))

      expectMsg(Address(address))

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone), member(address3, Manager))

      expectMsg(address)
      lastSender ! failure

      expectMsg(address2)
      lastSender ! failure

      expectMsg(address3)
      lastSender ! failure

      expectMsg(address)
      lastSender ! failure

      expectMsg(address)
      lastSender ! cluster(member(address2), member(address3, Clone))

      expectMsg(Address(address2))
    }

    "keep re-discovering for max attempts if best node failed" in new TestScope {
      actor ! GetAddress()

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))

      expectMsg(Address(address))
      actor ! GetAddress(Some(address))

      expectMsg(address2)
      lastSender ! cluster(member(address2), member(address3, Clone))

      expectMsg(Address(address2))
      actor ! GetAddress(Some(address2))

      expectMsg(address3)
      lastSender ! failure

      expectMsg(address)
      lastSender ! failure

      expectMsg(address)
      lastSender ! cluster(member(address, Manager), member(address2, ShuttingDown))

      expectFailure
    }

    "notify all clients about best node changed" in new TestScope {
      val clients = List.fill(5)(TestProbe())
      clients.foreach { client => actor.tell(GetAddress(), client.ref) }

      expectMsg(address)
      lastSender ! cluster(member(address), member(address2, Clone))

      clients.foreach { client => client expectMsg Address(address) }

      expectMsg(address)
      lastSender ! cluster(member(address2, Clone), member(address3))

      clients.foreach { client => client expectMsg Address(address3) }
      expectMsg(address3)
      lastSender ! failure

      expectMsg(address2)
      lastSender ! failure

      expectMsg(address)
      lastSender ! cluster(member(address2, Clone), member(address3))
      clients.foreach { client => client expectMsg Address(address3) }

      expectMsg(address3)
      lastSender ! cluster(member(address2), member(address3, isAlive = false))
      clients.foreach { client => client expectMsg Address(address2) }

      expectMsg(address2)
      lastSender ! failure

      expectMsg(address)
      lastSender ! cluster(member(address2, Clone), member(address3))
      clients.foreach { client => client expectMsg Address(address3) }
    }

    "remove client if terminated" in new TestScope {
      watch(actor)

      val probe = TestProbe()
      actor.tell(GetAddress(), probe.ref)

      expectMsg(address)
      lastSender ! cluster(member(address))

      probe.expectMsg(Address(address))
      system stop probe.ref

      expectMsg(address)
    }
  }

  trait TestScope extends ActorScope {
    implicit val timeout = Timeout(2.seconds)
    val address = "127.0.0.1" :: 1
    val address2 = "127.0.0.1" :: 2
    val address3 = "127.0.0.1" :: 3
    val ids = Map(
      (address, randomUuid),
      (address2, randomUuid),
      (address3, randomUuid)
    )
    val failure = Failure(TestException)

    val futureFunc: ClusterInfoOf.FutureFunc = address => testActor.ask(address).mapTo[ClusterInfo]

    lazy val actor = system.actorOf(ClusterDiscovererActor.props(settings, futureFunc))

    def expectFailure: ClusterException = {
      expectMsgPF() { case Failure(e: ClusterException) => e }
    }

    def settings = ClusterSettings(
      GossipSeedsOrDns.GossipSeeds(address),
      maxDiscoverAttempts = 3,
      discoverAttemptInterval = 100.millis,
      discoveryInterval = 300.millis
    )

    def cluster(members: MemberInfo*): ClusterInfo = ClusterInfo(address, members.toList)

    def member(
      address: InetSocketAddress,
      state:   NodeState         = NodeState.Leader,
      isAlive: Boolean           = true
    ): MemberInfo = MemberInfo(
      instanceId = ids(address),
      timestamp = ZonedDateTime.now,
      state = state,
      isAlive = isAlive,
      internalTcp = address,
      externalTcp = address,
      internalSecureTcp = address,
      externalSecureTcp = address,
      internalHttp = address,
      externalHttp = address,
      lastCommitPosition = 0,
      writerCheckpoint = 0,
      chaserCheckpoint = 0,
      epochPosition = 0,
      epochNumber = 0,
      epochId = randomUuid,
      nodePriority = 0
    )

    def future(x: ClusterInfo) = Future.successful(x)

    object TestException extends RuntimeException with NoStackTrace
  }
}