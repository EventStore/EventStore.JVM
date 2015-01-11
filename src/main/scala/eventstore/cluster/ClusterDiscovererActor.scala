package eventstore
package cluster

import java.net.{ InetAddress, InetSocketAddress }
import akka.actor._
import akka.actor.Status.Failure
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

private[eventstore] class ClusterDiscovererActor(
    settings: ClusterSettings,
    clusterInfo: ClusterInfo.FutureFunc) extends Actor with ActorLogging {
  import ClusterDiscovererActor._
  import context.dispatcher
  import settings._

  val tickInterval = 1.second
  val retryInterval = 500.millis

  override def preStart() = {
    super.preStart()
    self ! Tick
  }

  override def postRestart(reason: Throwable) = {}

  def receive = discovering(1, None, Set())

  def discovering(attempt: Int, failed: Option[InetSocketAddress], clients: Set[ActorRef]): Receive = {
    case GetAddress(_) =>
      val client = sender()
      context watch client
      context become discovering(attempt, failed, clients + client)

    case Tick => discover(attempt, clients, failed, Nil)

    case Terminated(client) if clients contains client =>
      context become discovering(attempt, failed, clients - client)
  }

  def discovered(bestNode: MemberInfo, members: List[MemberInfo], clients: Set[ActorRef]): Receive = {
    case GetAddress(failed) =>
      val client = sender()
      context watch client
      failed match {
        case Some(failed) if bestNode.externalTcp == failed => // TODO
          log.info("Cluster best node {} failed, reported by {}", bestNode, client)
          context become recovering(bestNode, members, clients + client)

        case _ =>
          client ! address(bestNode)
          context become discovered(bestNode, members, clients + client)
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
              val address = this.address(newBestNode)
              clients.foreach { client => client ! address }
            }
            context.system.scheduler.scheduleOnce(tickInterval, self, Tick)
            context become discovered(newBestNode, clusterInfo.members, clients)

          case None =>
            log.info("Cluster best node {} failed", bestNode)
            bestNodeFailed(bestNode, members, clients)
        }

      } catch {
        case NonFatal(e) =>
          log.info("Failed to reach cluster best node {} with error: {}", bestNode, e)
          bestNodeFailed(bestNode, members, clients)
      }

    case Terminated(client) if clients contains client =>
      context become discovered(bestNode, members, clients - client)
  }

  def recovering(failed: MemberInfo, members: List[MemberInfo], clients: Set[ActorRef]): Receive = {
    case GetAddress(_) =>
      val client = sender()
      context watch client
      context become recovering(failed, members, clients + client)

    case Tick => bestNodeFailed(failed, members, clients)

    case Terminated(client) if clients contains client =>
      context become recovering(failed, members, clients - client)
  }

  def discover(
    attempt: Int,
    clients: Set[ActorRef],
    failed: Option[InetSocketAddress],
    seeds: List[InetSocketAddress]): Unit = {
    def attemptFailed(e: Option[Throwable] = None): Unit = {
      if (attempt < maxDiscoverAttempts) {
        context.system.scheduler.scheduleOnce(retryInterval, self, Tick)
        context become discovering(attempt + 1, failed, clients)
      } else {
        val msg = e match {
          case Some(e) => s"Failed to discover candidate in $maxDiscoverAttempts attempts with error: $e"
          case None    => s"Failed to discover candidate in $maxDiscoverAttempts attempts"
        }
        log.error(msg)
        val failure = Failure(new ClusterException(msg, e))
        clients.foreach { client => client ! failure }
        context stop self
      }
    }

    try {
      val gossipSeeds = {
        if (seeds.nonEmpty) seeds
        else {
          val candidates = GossipCandidates(settings)
          failed.fold(candidates)(failed => seeds.filterNot(_ == failed)) match {
            case Nil   => candidates
            case seeds => seeds
          }
        }
      }
      // TODO method `find` doesn't pass exception
      val future = Future.find(gossipSeeds.map(clusterInfo))(_.bestNode.isDefined)

      // TODO
      Await.result(future, gossipTimeout /*TODO increase on retry*/ ) match {
        case Some(clusterInfo) =>
          val bestNode = clusterInfo.bestNode.get
          log.info("Discovering cluster: attempt {}/{} successful: best candidate is {}", attempt, maxDiscoverAttempts, bestNode)
          val address = this.address(bestNode)
          clients.foreach { client => client ! address }
          context.system.scheduler.scheduleOnce(tickInterval, self, Tick)
          context become discovered(bestNode, clusterInfo.members, clients)

        case None =>
          log.info("Discovering cluster: attempt {}/{} failed: no candidate found", attempt, maxDiscoverAttempts)
          attemptFailed(None)
      }
    } catch {
      case NonFatal(e) =>
        log.info("Discovering cluster: attempt {}/{} failed with error: {}", attempt, maxDiscoverAttempts, e)
        attemptFailed(Some(e))
    }
  }

  def bestNodeFailed(bestNode: MemberInfo, members: List[MemberInfo], clients: Set[ActorRef]): Unit = {
    // TODO move out to cluster
    // TODO optimise
    // TODO TEST
    val seeds = GossipCandidates(members.filterNot(_ like bestNode))
    discover(1, clients, Some(bestNode.externalHttp), seeds)
  }

  def address(x: MemberInfo) = Address(x.externalTcp)

  private case object Tick
}

private[eventstore] object ClusterDiscovererActor {

  def props(settings: ClusterSettings, clusterInfo: ClusterInfo.FutureFunc): Props = {
    Props(classOf[ClusterDiscovererActor], settings, clusterInfo)
  }

  // TODO might block // TODO TEST
  def resolveDns(dns: String): List[InetAddress] = {
    val result = try InetAddress.getAllByName(dns).toList catch {
      case NonFatal(e) =>
        throw new ClusterException(s"Error while resolving DNS entry $dns", Some(e))
    }
    if (result.isEmpty) throw new ClusterException(s"DNS entry '$dns' resolved into empty list")
    else result
  }

  object GossipCandidates {
    def apply(settings: ClusterSettings): List[InetSocketAddress] = {
      import GossipSeedsOrDns._
      val gossipSeeds = settings.gossipSeedsOrDns match {
        case GossipSeeds(x)        => x
        case ClusterDns(dns, port) => resolveDns(dns).map(x => x :: port)
      }
      Random.shuffle(gossipSeeds)
    }

    def apply(members: List[MemberInfo]): List[InetSocketAddress] = {
      val (nodes, managers) = members.filter(_.isAlive).partition(_.state != NodeState.Manager)
      (Random.shuffle(nodes) ::: Random.shuffle(managers)).map(_.externalHttp)
    }
  }

  case class GetAddress(failedEndPoint: Option[InetSocketAddress] = None /*TODO NodeEndpoints ?*/ )
  case class Address(value: InetSocketAddress)
}

class ClusterException(message: String, val cause: Option[Throwable]) extends EsException(message, cause.orNull) {
  def this(message: String) = this(message, None)

  override def toString = cause match {
    case Some(cause) => s"ClusterException($message, $cause)"
    case None        => s"ClusterException($message)"
  }
}