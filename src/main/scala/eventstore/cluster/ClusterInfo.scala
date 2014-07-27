package eventstore
package cluster

import java.net.InetSocketAddress
import java.util.Date
import akka.actor.ActorSystem
import scala.concurrent.Future

case class ClusterInfo(members: List[MemberInfo], serverAddress: InetSocketAddress)

object ClusterInfo {

  def future(address: InetSocketAddress)(implicit system: ActorSystem): Future[ClusterInfo] = {
    import system.dispatcher
    import ClusterProtocol._
    import spray.client.pipelining._
    import spray.http._
    import spray.httpx.SprayJsonSupport._

    val pipeline: HttpRequest => Future[ClusterInfo] = sendReceive ~> unmarshal[ClusterInfo]
    pipeline(Get(s"http:/$address/gossip?format=json"))
  }
}

case class MemberInfo(
  instanceId: Uuid,
  timeStamp: Date,
  state: NodeState,
  isAlive: Boolean,
  internalTcp: InetSocketAddress,
  internalSecureTcp: InetSocketAddress,
  externalTcp: InetSocketAddress,
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