package eventstore
package akka
package tcp

import javax.net.ssl._
import _root_.akka.actor._
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl._
import com.typesafe.sslconfig.akka.util.AkkaLoggerFactory

private[eventstore] object Tls {

  def createSSLContext(system: ActorSystem): SSLContext = {

    val mkLogger = new AkkaLoggerFactory(system)
    val settings = sslConfigSettings(system.settings.config)
    val keyManagerFactory = new DefaultKeyManagerFactoryWrapper(settings.keyManagerConfig.algorithm)
    val trustManagerFactory = new DefaultTrustManagerFactoryWrapper(settings.trustManagerConfig.algorithm)
    val builder = new ConfigSSLContextBuilder(mkLogger, settings, keyManagerFactory, trustManagerFactory)

    builder.build()
  }

  def createSSLEngine(sslContext: SSLContext): SSLEngine = {
    val engine = sslContext.createSSLEngine()
    engine.setUseClientMode(true)
    engine
  }

  def sslConfigSettings(config: Config): SSLConfigSettings = {
    val akkaOverrides = config.getConfig("akka.ssl-config")
    val defaults = config.getConfig("ssl-config")
    SSLConfigFactory.parse(akkaOverrides.withFallback(defaults))
  }

}
