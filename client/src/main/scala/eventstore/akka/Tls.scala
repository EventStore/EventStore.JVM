package eventstore
package akka

import javax.net.ssl._
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl._
import com.typesafe.sslconfig.akka.util.AkkaLoggerFactory
import _root_.akka.actor._

private[eventstore] object Tls {

  def createSSLContext(system: ActorSystem): SSLContext = {

    val mkLogger = new AkkaLoggerFactory(system)
    val settings = sslConfigSettings(system.settings.config)
    val keyManagerFactory = new DefaultKeyManagerFactoryWrapper(settings.keyManagerConfig.algorithm)
    val trustManagerFactory = new DefaultTrustManagerFactoryWrapper(settings.trustManagerConfig.algorithm)
    val builder = new ConfigSSLContextBuilder(mkLogger, settings, keyManagerFactory, trustManagerFactory)

    builder.build()
  }

  def createSSLEngine(host: String, port: Int, sslContext: SSLContext): SSLEngine = {
    val engine = sslContext.createSSLEngine(host, port)
    engine.setUseClientMode(true)

    engine.setSSLParameters({
       val params = engine.getSSLParameters
       params.setEndpointIdentificationAlgorithm("https")
       params
    })

    engine
  }

  def sslConfigSettings(config: Config): SSLConfigSettings = {
    val overrides = config.getConfig("eventstore.ssl-config")
    val defaults = config.getConfig("ssl-config")
    SSLConfigFactory.parse(overrides.withFallback(defaults))
  }

}
