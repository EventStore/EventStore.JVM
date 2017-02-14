package eventstore
package cluster

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.HostConnectionPool
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.joda.time.DateTime

import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.util.Try

@SerialVersionUID(1L)
case class ClusterInfo(serverAddress: InetSocketAddress, members: List[MemberInfo]) {
  lazy val bestNode: Option[MemberInfo] = {
    val xs = members.filter { x => x.isAlive && x.state.isAllowedToConnect }
    if (xs.isEmpty) None else Some(xs.maxBy(_.state))
  }
}

object ClusterInfo {

  type FutureFunc = InetSocketAddress => Future[ClusterInfo]

  def futureFunc(implicit system: ActorSystem): FutureFunc = {
    import ClusterProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import system.dispatcher

    val http = Http(system)
    val acceptHeader = headers.Accept(MediaRange(MediaTypes.`application/json`))
    implicit val materializer = ActorMaterializer()

    val pools = TrieMap.empty[Uri, Flow[(HttpRequest, Unit), (Try[HttpResponse], Unit), HostConnectionPool]]

    def clusterInfo(address: InetSocketAddress) = {
      val host = address.getHostString
      val port = address.getPort
      val uri = Uri(s"http://$host:$port/gossip?format=json")
      val req = HttpRequest(uri = uri, headers = List(acceptHeader))
      val pool = pools.getOrElseUpdate(uri, http.cachedHostConnectionPool[Unit](host, port))
      val source = Source.single((req, ()))
      val (_, response) = pool.runWith(source, Sink.head)
      for {
        (response, _) <- response
        clusterInfo <- Unmarshal(response.get).to[ClusterInfo]
      } yield clusterInfo
    }

    clusterInfo
  }
}

@SerialVersionUID(1L)
case class MemberInfo(
    instanceId:         Uuid,
    timestamp:          DateTime,
    state:              NodeState,
    isAlive:            Boolean,
    internalTcp:        InetSocketAddress,
    externalTcp:        InetSocketAddress,
    internalSecureTcp:  InetSocketAddress,
    externalSecureTcp:  InetSocketAddress,
    internalHttp:       InetSocketAddress,
    externalHttp:       InetSocketAddress,
    lastCommitPosition: Long,
    writerCheckpoint:   Long,
    chaserCheckpoint:   Long,
    epochPosition:      Long,
    epochNumber:        Int,
    epochId:            Uuid,
    nodePriority:       Int
) extends Ordered[MemberInfo] {

  def compare(that: MemberInfo) = this.state compare that.state

  def like(other: MemberInfo): Boolean = this.instanceId == other.instanceId
}