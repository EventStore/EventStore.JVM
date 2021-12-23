package eventstore
package akka

import javax.net.ssl.{SSLContext, SSLEngine, X509TrustManager}
import _root_.akka.actor._
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl._
import com.typesafe.sslconfig.akka.util.AkkaLoggerFactory

private[eventstore] object Tls {

  def createSSLContext(system: ActorSystem): SSLContext =
    mkSslContextAndTM(system)._1

  def createSSLContextAndTrustManager(system: ActorSystem): (SSLContext, X509TrustManager) =
    mkSslContextAndTM(system)

  private def mkSslContextAndTM(system: ActorSystem): (SSLContext, X509TrustManager) = {

    val mkLogger = new AkkaLoggerFactory(system)
    val settings = sslConfigSettings(system.settings.config)

    val signatureConstraints = settings.disabledSignatureAlgorithms.map(AlgorithmConstraintsParser.apply).toSet
    val keySizeConstraints = settings.disabledKeyAlgorithms.map(AlgorithmConstraintsParser.apply).toSet
    val algorithmChecker = new AlgorithmChecker(mkLogger, signatureConstraints, keySizeConstraints)
    val keyManagerFactory = new DefaultKeyManagerFactoryWrapper(settings.keyManagerConfig.algorithm)
    val trustManagerFactory = new DefaultTrustManagerFactoryWrapper(settings.trustManagerConfig.algorithm)

    val builder = new ConfigSSLContextBuilder(mkLogger, settings, keyManagerFactory, trustManagerFactory)

    val tm: X509TrustManager = builder.buildCompositeTrustManager(
      settings.trustManagerConfig,
      settings.checkRevocation.getOrElse(false),
      builder.certificateRevocationList(settings),
      algorithmChecker,
      settings.debug
    )

    (builder.build(), tm)
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