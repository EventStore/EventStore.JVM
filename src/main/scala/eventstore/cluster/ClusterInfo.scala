package eventstore
package cluster

import java.net.{ Inet6Address, InetSocketAddress }
import java.util.Date
import akka.actor.ActorSystem
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try

case class ClusterInfo(serverAddress: InetSocketAddress, members: List[MemberInfo]) {
  lazy val bestNode: Option[NodeEndpoints] = members
    .filter(x => x.isAlive && x.state.isAllowedToConnect)
    .sortBy(_.state)
    .headOption
    .map { member => NodeEndpoints(member.externalTcp /*TODO*/ , member.externalSecureTcp /*TODO*/ ) }
}

object ClusterInfo {

  def future(address: InetSocketAddress)(implicit system: ActorSystem): Future[ClusterInfo] = {
    import system.dispatcher
    import ClusterProtocol._
    import spray.client.pipelining._
    import spray.http._
    import spray.httpx.SprayJsonSupport._

    // TODO what is a proper way to convert InetSocketAddress to Uri
    val host = address.getAddress match {
      case address: Inet6Address => s"[${address.getHostAddress}]"
      case address               => address.getHostAddress
    }
    val port = address.getPort
    val uri = Uri(s"http://$host:$port/gossip?format=json")
    val pipeline: HttpRequest => Future[ClusterInfo] = sendReceive ~> unmarshal[ClusterInfo]
    pipeline(Get(uri))
  }

  def opt(address: InetSocketAddress)(implicit system: ActorSystem): Option[ClusterInfo] = {
    Try(Await.result(future(address), 10.seconds /*TODO*/ )).toOption
  }
}

case class MemberInfo(
  instanceId: Uuid,
  timestamp: Date,
  state: NodeState,
  isAlive: Boolean,
  internalTcp: InetSocketAddress,
  externalTcp: InetSocketAddress,
  internalSecureTcp: InetSocketAddress,
  externalSecureTcp: InetSocketAddress,
  internalHttp: InetSocketAddress,
  externalHttp: InetSocketAddress,
  lastCommitPosition: Long,
  writerCheckpoint: Long,
  chaserCheckpoint: Long,
  epochPosition: Long,
  epochNumber: Int,
  epochId: Uuid,
  nodePriority: Int)