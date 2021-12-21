package eventstore
package akka

import scala.jdk.CollectionConverters._
import _root_.akka.actor.ActorSystem
import okhttp3.{ConnectionSpec, OkHttpClient}
import sttp.client3.SttpBackend
import sttp.client3.okhttp.OkHttpFutureBackend

import scala.concurrent.Future

private[eventstore] object Http {

  def mkClient(useTls: Boolean, system: ActorSystem): OkHttpClient = {
    val builder: OkHttpClient.Builder = new OkHttpClient.Builder()

    if(useTls) {
      val (sc, tm) = Tls.createSSLContextAndTrustManager(system)
      builder.sslSocketFactory(sc.getSocketFactory, tm.orNull).connectionSpecs(List(ConnectionSpec.MODERN_TLS).asJava)
    } else
      builder.connectionSpecs(List(ConnectionSpec.CLEARTEXT).asJava)

    builder.build()
  }


  def mkSttpFutureBackend(useTls: Boolean, system: ActorSystem): SttpBackend[Future, Any] =
    OkHttpFutureBackend.usingClient(mkClient(useTls, system))

}
