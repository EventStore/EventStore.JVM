package eventstore
package akka

import scala.concurrent.Future
import _root_.akka.actor.ActorSystem
import okhttp3._
import sttp.client3._
import sttp.client3.okhttp.OkHttpFutureBackend

private[eventstore] object Http {

  private def mkOkHttpClient(useTls: Boolean, system: ActorSystem): OkHttpClient = {
    val builder = new OkHttpClient.Builder()
    if(useTls) {
      val (sc, tm) = Tls.createSSLContextAndTrustManager(system)
      builder.sslSocketFactory(sc.getSocketFactory, tm).build()
    } else
      builder.build()
  }

  def mkSttpFutureBackend(useTls: Boolean, system: ActorSystem): SttpBackend[Future, Any] =
    OkHttpFutureBackend.usingClient(mkOkHttpClient(useTls, system))

}
