package eventstore
package cluster

import java.net.{ InetAddress, InetSocketAddress }
import akka.actor.Status.Failure
import akka.actor._
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

case class ClusterException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

class ClusterDiscovererActor(settings: ClusterSettings) extends Actor with ActorLogging {
  import ClusterDiscovererActor._
  import context.dispatcher
  import context.system
  import settings._

  var _oldGossip: List[MemberInfo] = Nil

  def receive = {
    case GetNode(failedEndPoint) => makeAttempt(sender(), 1, failedEndPoint)
  }

  def makeAttempt(actor: ActorRef, attempt: Int, failedEndPoint: Option[InetSocketAddress]) = {
    def makeAttempt(attempt: Int): Unit = {
      try {
        discoverCandidate(failedEndPoint) match {
          case None =>
            log.warning(s"Discovering cluster. Attempt {}/{} failed: no candidate found.", attempt, maxDiscoverAttempts)
            if (attempt >= maxDiscoverAttempts) {
              actor ! Failure(new ClusterException(s"Failed to discover candidate in $maxDiscoverAttempts attempts."))
              context become receive
            } else {
              context.system.scheduler.scheduleOnce(500.millis /*TODO*/ , self, MakeAttempt)
              context become {
                case MakeAttempt => makeAttempt(attempt + 1)
              }
            }
          case Some(x) =>
            log.info(s"Discovering cluster. Attempt {}/{} successful: best candidate is $x.", attempt, maxDiscoverAttempts)
            actor ! GotNode(x)
            context become receive
        }
      } catch {
        case e: ClusterException =>
          actor ! Failure(e)
          context become receive
        case NonFatal(e) =>
          actor ! Failure(new ClusterException("Error while discovering candidate", e))
          context become receive
      }
    }

    makeAttempt(attempt)
  }

  def discoverCandidate(failed: Option[InetSocketAddress]): Option[NodeEndpoints] = {
    val candidates = _oldGossip match {
      case Nil => GossipCandidates(settings)
      case xs  => GossipCandidates(xs, failed)
    }

    val candidate = candidates.collectFirst { case ClusterWithBestNode(a, b) => (a, b) }

    _oldGossip = candidate.fold(List.empty[MemberInfo]) { case (clusterInfo, _) => clusterInfo.members }

    candidate.map { case (_, bestNode) => bestNode }
  }
}

object ClusterDiscovererActor {

  def props(settings: ClusterSettings): Props = Props(
    classOf[ClusterDiscovererActor],
    settings)

  def resolveDns(dns: String): List[InetAddress] = try {
    val result = InetAddress.getAllByName(dns).toList
    if (result.isEmpty) throw new ClusterException(s"DNS entry '$dns' resolved into empty list.")
    result
  } catch {
    case NonFatal(e) => throw new ClusterException(s"Error while resolving DNS entry $dns", e)
  }

  object ClusterWithBestNode {
    def unapply(x: InetSocketAddress)(implicit system: ActorSystem): Option[(ClusterInfo, NodeEndpoints)] = for {
      gossip <- ClusterInfo.opt(x)
      bestNode <- gossip.bestNode
    } yield (gossip, bestNode)
  }

  object GossipCandidates {
    def apply(settings: ClusterSettings) = {
      import GossipSeedsOrDns._
      val gossipSeeds = settings.gossipSeedsOrDns match {
        case GossipSeeds(x)        => x
        case ClusterDns(dns, port) => resolveDns(dns).map(x => x :: port)
      }
      Random.shuffle(gossipSeeds)
    }

    def apply(members: List[MemberInfo], failed: Option[InetSocketAddress]): List[InetSocketAddress] = {
      val candidates = failed.fold(members) { x => members.filterNot(_.externalTcp == x) }
      arrange(candidates)
    }

    private def arrange(members: List[MemberInfo]): List[InetSocketAddress] = {
      val (nodes, managers) = members.partition(_.state != NodeState.Manager)
      (Random.shuffle(nodes) ::: Random.shuffle(managers)).map(_.externalHttp)
    }
  }

  case class GetNode(failedEndPoint: Option[InetSocketAddress] = None)

  case class GotNode(endpoints: NodeEndpoints)

  case object MakeAttempt
}
