package eventstore.examples

import akka.actor._
import eventstore.cluster.ClusterDiscovererActor.{ GetAddress, Address }
import eventstore.cluster.GossipSeedsOrDns.GossipSeeds
import eventstore.cluster.{ ClusterSettings, ClusterInfo, ClusterDiscovererActor }
import eventstore.EsInt

object DiscoverCluster extends App {
  implicit val system = ActorSystem()
  val settings = ClusterSettings(GossipSeeds(
    "127.0.0.1" :: 1113,
    "127.0.0.1" :: 2113,
    "127.0.0.1" :: 3113
  ))
  val discoverer = system.actorOf(ClusterDiscovererActor.props(settings, ClusterInfo.futureFunc), "discoverer")
  system.actorOf(Props(classOf[DiscoverCluster], discoverer))
}

class DiscoverCluster(discoverer: ActorRef) extends Actor with ActorLogging {
  override def preStart() = discoverer ! GetAddress()

  def receive = {
    case Address(bestNode) => log.info("Best Node: {}", bestNode)
  }
}