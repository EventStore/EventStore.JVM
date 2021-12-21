//package eventstore
//package akka
//package cluster
//
//import java.net.InetSocketAddress
//import javax.net.ssl.SSLContext
//import scala.collection.concurrent.TrieMap
//import scala.concurrent._
//import scala.util.Try
//import _root_.akka.actor.ActorSystem
//import _root_.akka.http.scaladsl.Http.HostConnectionPool
//import _root_.akka.stream.scaladsl._
//import _root_.akka.http.scaladsl.{ConnectionContext, Http}
//import _root_.akka.http.scaladsl.model._
//import _root_.akka.http.scaladsl.unmarshalling.Unmarshal
//import _root_.akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
//import eventstore.core.cluster.ClusterInfo
//
//private[eventstore] object ClusterInfoOf {
//
//  type FutureFunc = InetSocketAddress => Future[ClusterInfo]
//
//  def apply(useTls: Boolean)(implicit system: ActorSystem): FutureFunc = {
//
//    import SprayJsonSupport._
//    import ClusterJsonProtocol._
//    import system.dispatcher
//
//    val http = Http(system)
//    val acceptHeader = headers.Accept(MediaRange(MediaTypes.`application/json`))
//    val sslContext: Option[SSLContext] = if(useTls) Some(Tls.createSSLContext(system)) else None
//
//    val pools = TrieMap.empty[Uri, Flow[(HttpRequest, Unit), (Try[HttpResponse], Unit), HostConnectionPool]]
//
//    def clusterInfo(address: InetSocketAddress) = {
//
//      val protocol = if(useTls) "https" else "http"
//      val host = address.getHostString
//      val port = address.getPort
//      val uri = Uri(s"$protocol://$host:$port/gossip?format=json")
//
//      val connectionPool = sslContext match {
//        case Some(sc) =>
//          val cc = ConnectionContext.httpsClient(sc)
//          http.cachedHostConnectionPoolHttps[Unit](uri.authority.host.address(), uri.authority.port, cc)
//        case None =>
//          http.cachedHostConnectionPool[Unit](uri.authority.host.address(), uri.authority.port)
//      }
//
//      val req = HttpRequest(uri = uri, headers = List(acceptHeader))
//      val pool = pools.getOrElseUpdate(uri, connectionPool)
//      val source = Source.single((req, ()))
//      val (_, response) = pool.runWith(source, Sink.head)
//      for {
//        (response, _) <- response
//        clusterInfo <- Unmarshal(response.get).to[ClusterInfo]
//      } yield clusterInfo
//    }
//
//    clusterInfo
//  }
//}
//
