package eventstore
package akka
package cluster

import _root_.akka.actor.ActorSystem

import java.net.InetSocketAddress
import scala.concurrent._
import sttp.client3._
import sttp.client3.sprayJson._
import eventstore.core.cluster.ClusterInfo
import sttp.model.MediaType

private[eventstore] object ClusterInfoOf {

  type FutureFunc = InetSocketAddress => Future[ClusterInfo]

  def apply(useTls: Boolean)(implicit system: ActorSystem): FutureFunc = {

    import ClusterJsonProtocol._
    import system.dispatcher

    val sttp = Http.mkSttpFutureBackend(useTls, system)

    def clusterInfo(address: InetSocketAddress): Future[ClusterInfo] = {

      val protocol = if(useTls) "https" else "http"
      val host = address.getHostString
      val port = address.getPort
      val uri = uri"$protocol://$host:$port/gossip?format=json"

      basicRequest
        .get(uri)
        .contentType(MediaType.ApplicationJson)
        .response(asJson[ClusterInfo].getRight)
        .send(sttp)
        .map(_.body)
    }

    clusterInfo
  }
}

