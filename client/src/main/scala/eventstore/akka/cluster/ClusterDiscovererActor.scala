package eventstore
package akka
package cluster

import java.net.InetSocketAddress
import scala.concurrent.{Await, Future}
import scala.util.Random.shuffle
import scala.util.control.NonFatal
import _root_.akka.actor._
import _root_.akka.actor.Status.Failure
import eventstore.syntax._
import eventstore.cluster.{ClusterSettings, ResolveDns, MemberInfo, ClusterException, NodeState}

private[eventstore] class ClusterDiscovererActor(
    settings:    ClusterSettings,
    clusterInfo: ClusterInfoOf.FutureFunc
) extends Actor with ActorLogging {
  import ClusterDiscovererActor._
  import context.dispatcher
  import settings._

  var clients: Set[ActorRef] = Set()

  val rcvTerminated: Receive = {
    case Terminated(client) if clients contains client => clients = clients - client
  }

  override def preStart() = self ! Tick

  def receive = discovering(1, None)

  def discovering(attempt: Int, failed: Option[InetSocketAddress]): Receive = rcvTerminated or {
    case GetAddress(_) => addClient(sender())
    case Tick          => discover(attempt, failed, Nil)
  }

  def discovered(bestNode: MemberInfo, members: List[MemberInfo]): Receive = {
    context.system.scheduler.scheduleOnce(discoveryInterval, self, Tick)
    rcvTerminated or {
      case GetAddress(failed) =>
        val client = sender()
        addClient(client)
        failed match {
          case Some(addr) if bestNode.externalTcp == addr =>
            log.info("Cluster best node {} failed, reported by {}", bestNode, client)
            context become recovering(bestNode, members)

          case _ => client ! Address(bestNode)
        }

      case Tick =>
        try {
          val future = this.clusterInfo(bestNode.externalHttp)
          val clusterInfo = Await.result(future, gossipTimeout)
          clusterInfo.bestNode match {
            case Some(newBestNode) =>
              if (newBestNode like bestNode) {
                val state = bestNode.state
                val newState = newBestNode.state
                if (state != newState) log.info("Cluster best node state changed from {} to {}", state, newState)
              } else {
                log.info("Cluster best node changed from {} to {}", bestNode, newBestNode)
                broadcast(Address(newBestNode))
              }
              context become discovered(newBestNode, clusterInfo.members)

            case None =>
              log.info("Cluster best node {} failed", bestNode)
              bestNodeFailed(bestNode, members)
          }
        } catch {
          case NonFatal(e) =>
            log.info("Failed to reach cluster best node {} with error: {}", bestNode, e)
            bestNodeFailed(bestNode, members)
        }
    }
  }

  def recovering(failed: MemberInfo, members: List[MemberInfo]): Receive = rcvTerminated or {
    case GetAddress(_) => addClient(sender())
    case Tick          => bestNodeFailed(failed, members)
  }

  def discover(attempt: Int, failed: Option[InetSocketAddress], seeds: List[InetSocketAddress]) = {

    def attemptFailed(e: Option[Throwable]) = {
      if (attempt < maxDiscoverAttempts) {
        e match {
          case Some(th) => log.info("Discovering cluster: attempt {}/{} failed with error: {}", attempt, maxDiscoverAttempts, th)
          case None     => log.info("Discovering cluster: attempt {}/{} failed: no candidate found", attempt, maxDiscoverAttempts)
        }
        context.system.scheduler.scheduleOnce(discoverAttemptInterval, self, Tick)
        context become discovering(attempt + 1, failed)
      } else {
        val msg = e match {
          case Some(th) => s"Failed to discover candidate in $maxDiscoverAttempts attempts with error: $th"
          case None     => s"Failed to discover candidate in $maxDiscoverAttempts attempts"
        }
        log.error(msg)
        broadcast(Failure(new ClusterException(msg, e)))
        context stop self
      }
    }

    try {
      val gossipSeeds = {
        if (seeds.nonEmpty) seeds
        else {
          val candidates = GossipCandidates(settings)
          failed.fold(candidates)(failed => seeds.filterNot(_ == failed)) match {
            case Nil => candidates
            case ss  => ss
          }
        }
      }

      val futures = gossipSeeds.map { gossipSeed =>
        val future = clusterInfo(gossipSeed)
        future.failed foreach { x =>
          log.debug("Failed to get cluster info from {}: {}", gossipSeed, x)
        }
        future
      }

      val future = Future.find(futures)(_.bestNode.isDefined)
      Await.result(future, gossipTimeout) match {
        case None => attemptFailed(None)
        case Some(ci) =>
          val bestNode = ci.bestNode.get
          log.info("Discovering cluster: attempt {}/{} successful: best candidate is {}", attempt, maxDiscoverAttempts, bestNode)
          broadcast(Address(bestNode))
          context become discovered(bestNode, ci.members)
      }
    } catch { case NonFatal(e) => attemptFailed(Some(e)) }
  }

  def addClient(client: ActorRef) = clients = clients + context.watch(client)

  def broadcast(x: AnyRef) = clients.foreach { client => client ! x }

  def bestNodeFailed(bestNode: MemberInfo, members: List[MemberInfo]) = {
    val seeds = GossipCandidates(members.filterNot(_ like bestNode))
    discover(1, Some(bestNode.externalHttp), seeds)
  }

  case object Tick

  object GossipCandidates {
    def apply(settings: ClusterSettings): List[InetSocketAddress] = {
      import eventstore.cluster.GossipSeedsOrDns._
      val gossipSeeds = settings.gossipSeedsOrDns match {
        case GossipSeeds(x)        => x
        case ClusterDns(dns, port) => ResolveDns(dns, dnsLookupTimeout).map(x => x :: port)
      }
      shuffle(gossipSeeds)
    }

    def apply(members: List[MemberInfo]): List[InetSocketAddress] = {
      val (nodes, managers) = members.filter(_.isAlive).partition(_.state != NodeState.Manager)
      (shuffle(nodes) ::: shuffle(managers)).map(_.externalHttp)
    }
  }
}

private[eventstore] object ClusterDiscovererActor {

  def props(settings: ClusterSettings, clusterInfo: ClusterInfoOf.FutureFunc): Props = {
    Props(new ClusterDiscovererActor(settings, clusterInfo))
  }

  @SerialVersionUID(1L) final case class GetAddress(failed: Option[InetSocketAddress])
  object GetAddress {
    def apply(): GetAddress = GetAddress(None)
  }

  @SerialVersionUID(1L) final case class Address(value: InetSocketAddress)

  object Address {
    def apply(x: MemberInfo): Address = Address(x.externalTcp)
  }
}