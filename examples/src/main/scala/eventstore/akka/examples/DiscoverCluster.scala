package eventstore
package akka
package examples

import java.net.InetSocketAddress
import _root_.akka.actor._
import eventstore.cluster.GossipSeedsOrDns.GossipSeeds
import eventstore.cluster.ClusterSettings
import eventstore.akka.cluster.ClusterDiscovererActor.{Address, GetAddress}
import eventstore.akka.cluster.ClusterDiscovererActor
import eventstore.akka.cluster.ClusterInfoOf

object DiscoverCluster extends App {
  implicit val system = ActorSystem()
  val settings = ClusterSettings(GossipSeeds(
    new InetSocketAddress("127.0.0.1", 1113),
    new InetSocketAddress("127.0.0.1", 2113),
    new InetSocketAddress("127.0.0.1", 3113)
  ))
  val discoverer = system.actorOf(ClusterDiscovererActor.props(settings, ClusterInfoOf(), useTls = false), "discoverer")
  system.actorOf(Props(classOf[DiscoverCluster], discoverer))
}

class DiscoverCluster(discoverer: ActorRef) extends Actor with ActorLogging {
  override def preStart() = discoverer ! GetAddress()

  def receive = {
    case Address(bestNode) => log.info("Best Node: {}", bestNode)
  }
}